package main.scala

import scala.collection.mutable.ArrayBuffer
import akka.actor._
import akka.actor.Props
import scala.math._
import scala.concurrent.duration._
import scala.util.Random

class Node(base: Int, maxBitLength: Int, knownNode: ActorRef, monitor:ActorRef) extends Actor {

  /*by default: b = 4, N = 2^32, rownum = 8, colnum = 15
	 * with each nodeId has 32 bits, ie, 4 bytes, like oxFFFFFFFF.
	 * SHA1 return a number with 160 bits(20 bytes), that is a hex string with length 40, we need only a string of length 8,
	 * so we need first 4 bytes of SHA1 result.
	 */
  
  val byteLength = maxBitLength / 8;
  val routeTableRowNum = maxBitLength / base
  val routeTableColNum: Int = (Math.pow(base, 2)).toInt
  val routeTable = Array.ofDim[NodeIdWithRef](routeTableRowNum, routeTableColNum)
  val nodeId: String = SHA1.getHexString(self.toString, byteLength)
  var idref = new NodeIdWithRef(nodeId, self)
  //when a node joins the network, it keeps track of the path through which its information has been routed to,
  //when those nodes sent their route tables to this node, this node sends its route information to all of them.
  var joinNetworkPathNodes = ArrayBuffer[ActorRef]();
  var receivedAllPathInfo = false
  var pathNodeNum = -1
  var pathNodeMaxNum = -1
  //	selfReport

  pastryInit(knownNode)

  def closer(incomingId: String, oldId: String): Boolean = {
    if (oldId == null) return true
    
    val nodeidInt = java.lang.Long.parseLong(nodeId, 16)
    val oldInt = java.lang.Long.parseLong(oldId, 16)
    val newInt = java.lang.Long.parseLong(incomingId, 16)
    

    if (Math.abs(nodeidInt - newInt) < Math.abs(nodeidInt - oldInt)) return true
    return false
  }

  def routeJoinForward(nodeIdRef: NodeIdWithRef,time:Int): Unit = {
    //when X is forwarded to other nodes, the nodes send their route table to X.
    //and the nodes route X to next node until X reach its closest node

    val incomingId = nodeIdRef.getnodeId
    val row = getCommonPrefixLen(nodeId, incomingId) // row == commonPrefixLength

    //if two actor has the same hash value
    if (row == routeTable.length) {
//      println("nodeId conflict......fatal errer!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
      nodeIdRef.getRef ! routeTableMsgToX(routeTable,time+1, 2)
      return
    }

    val nextch = incomingId.charAt(row)
    val col = Integer.parseInt("" + nextch, 16)
    //found incoming node should be in routeTable[row][col], which may be null.
    if (routeTable(row)(col) != null) {
      routeTable(row)(col).getRef ! join(nodeIdRef,time+1)
      nodeIdRef.getRef ! routeTableMsgToX(routeTable,time+1, 0)
    } else {
      val closestNode = getClosetNode(incomingId)
      //if the closest node is current node or not found, the propagation is terminated
      if (closestNode != null &&
        getCommonPrefixLen(incomingId, closestNode.getnodeId) >= getCommonPrefixLen(incomingId, nodeId)
        && getDiff(closestNode.getnodeId, incomingId) < getDiff(nodeId, incomingId)) {
        nodeIdRef.getRef ! routeTableMsgToX(routeTable, time+1, 0)
        closestNode.getRef ! join(nodeIdRef, time+1)
      } else {
        nodeIdRef.getRef ! routeTableMsgToX(routeTable,time+1, 1)
      }
    }
  }

  def getDiff(nodeId1: String, nodeId2: String):java.lang.Long = {
    val int1 = java.lang.Long.parseLong(nodeId1, 16)
    val int2 = java.lang.Long.parseLong(nodeId2, 16)
    return Math.abs(int1 - int2)

  }
  //when corresponding position in the route table is null, pick a closest node current node knows as a next route node.
  // the closest node found should be closer to incoming node than current node
  def getClosetNode(incomingId: String): NodeIdWithRef = {
    var mindiff = java.lang.Long.MAX_VALUE
    var closest: NodeIdWithRef = null
    for (i <- 0 until routeTable.length) {
      for (j <- 0 until routeTable(0).length)
        if (routeTable(i)(j) != null) {
          var diff = getDiff(routeTable(i)(j).getnodeId, incomingId)
          if (diff < mindiff) {
            closest = routeTable(i)(j)
            mindiff = diff
          }
        }
    }
    return closest
  }
  
  def getCommonPrefixLen(str0: String, str: String): Int = {
    var i: Int = 0;
    while (i < str0.length && str.charAt(i) == str0.charAt(i)) {
      i += 1
    }
    return i
  }
  def reftoNodeId(node: ActorRef): String = {
    return SHA1.getHexString(node.toString, byteLength)
  }
  
  /* decide which node the meg(key,hop) should be forward to.
   * if the returned node is the current local node, it means this node is closest to the key, and routing ends
   */
  def getNextRouteNode(key:String, hop:Int):NodeIdWithRef = {
    if( key == nodeId)
      return idref
    val row = getCommonPrefixLen(nodeId, key) // row == commonPrefixLength
    val nextch = key.charAt(row)
    val col = Integer.parseInt("" + nextch, 16)
    if (routeTable(row)(col) != null) {
      return routeTable(row)(col)
    } else {
      val closestNode = getClosetNode(key)
      //if the closest node is current node or not found, the propagation is terminated
      if (closestNode != null &&
        getCommonPrefixLen(key, closestNode.getnodeId) >= getCommonPrefixLen(key, nodeId)
        && getDiff(closestNode.getnodeId, key) < getDiff(nodeId, key)) {
        return closestNode
      } else {
        return idref
      }
    }
  }
  
  def receive = {
    case Request(key:String, hop:Int)=>
//      println("msg routing track: " + key + " passes through node " + nodeId)
      val nextNode = getNextRouteNode(key,hop)
      if( nextNode == idref)
        monitor ! MsgDelivered(hop)
      else
        nextNode.getRef ! Request(key, hop+1)
    case join(comingnode: NodeIdWithRef, time:Int) =>
//      println(comingnode.getnodeId + " join through " + idref.getnodeId)
      //forward the join information to other nodes.
      routeJoinForward(comingnode,time)
    case routeTableMsgToX(rt: Array[Array[NodeIdWithRef]],num:Int, state: Int) =>
      //println("received a path infomation from " + reftoNodeId(sender))
      if (state == 2) {
        monitor ! NodeShutDown
        terminate
      } else {
        if(state == 1){
          receivedAllPathInfo = true
          pathNodeMaxNum = num
        }
        updateRouteTableOneElem(new NodeIdWithRef(reftoNodeId(sender), sender))
        updateRouteTable(rt: Array[Array[NodeIdWithRef]])
        joinNetworkPathNodes.append(sender)
        if (receivedAllPathInfo && pathNodeMaxNum == joinNetworkPathNodes.length) {
          //after new node is initialized, it tells the monitor and sends its route to nodes to the path nodes.
          monitor ! NodeInitDone
          for (node <- joinNetworkPathNodes) {
            node ! routeTableMsg(routeTable)
          }
        }
      }
    case routeTableMsg(rt: Array[Array[NodeIdWithRef]]) =>
      updateRouteTableOneElem(new NodeIdWithRef(reftoNodeId(sender), sender))
      updateRouteTable(rt: Array[Array[NodeIdWithRef]])
    //routeTableReport
  }

  def updateRouteTable(rt: Array[Array[NodeIdWithRef]]) = {
    for (i <- 0 until rt.length)
      for (j <- 0 until rt(0).length)
        updateRouteTableOneElem(rt(i)(j))
  }

  //given a certain node, update the corresponding route table position
  def updateRouteTableOneElem(node: NodeIdWithRef) {
    if (node == null || node.getnodeId == nodeId)
      return
    val row = getCommonPrefixLen(nodeId, node.getnodeId) // row == commonPrefixLength
    val nextch = node.getnodeId.charAt(row)
    val col = Integer.parseInt("" + nextch, 16)
    if (routeTable(row)(col) == null || getDiff(node.getnodeId, nodeId) < getDiff(routeTable(row)(col).getnodeId, nodeId))
      routeTable(row)(col) = node;
  }

  def terminate = {
//    println("I " + nodeId + " has met a conflict in the pastry network")
    context.stop(self)
  }

  //to be continued
  def joinNetwork(knownNode: ActorRef) = {
    knownNode ! join(idref,0)
  }

  def pastryInit(knownNode: ActorRef) = {
    if (knownNode != null)
      joinNetwork(knownNode)
    else
      monitor ! NodeInitDone
  }

  def routeTableReport = {
    println("......routeTable of " + nodeId)
    for (i <- 0 until routeTable.length) {
      for (j <- 0 until routeTable(0).length) {
        if (routeTable(i)(j) == null)
          print("null ")
        else
          print(" " + routeTable(i)(j).getnodeId)
      }
      println
    }
    println("......end of route table info.......")
  }
  def selfReport = {
    println("actorRef:" + self)
    println("nodeid:" + nodeId)
  }

}