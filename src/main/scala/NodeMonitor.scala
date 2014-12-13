//this monitor is not supposed to exist in reality. I use this actor to simulate the whole pastry network

package main.scala

import scala.collection.mutable.ArrayBuffer;
import akka.actor._;
import akka.actor.Props
import scala.math._
import scala.concurrent.duration._
import scala.util.Random

class NodeMonitor(base: Int, maxBitLength: Int, nodesNum:Int, requestSum:Int) extends Actor {

  val routeTableRowNum = maxBitLength / base;
  val routeTableColNum: Int = (Math.pow(base, 2)).toInt;
  var actorpool = ArrayBuffer[ActorRef]();
  val random = Random
  val byteLength = maxBitLength / 8;

  val interval = 10 milliseconds
  var timer:Cancellable = null
  var nodesInNetwork = 0;
  var networkSetDone = false;
  var hopSum = 0
  var msgSum = 0
  var requestNum = 0
  val randomStrs = Array("str1", "str2","str3","str4","str5","str6","str7","str8","str9","str10")
  
  addActorRepeatly
  val requestInterval = 1 seconds


  case object ShutDown
  case object AddNode
  case object SendRequest

  def addActorRepeatly={
    println("building pastry network...")
    val system = context.system
    import system.dispatcher
    timer = context.system.scheduler.schedule(0 milliseconds, interval, self, AddNode)
  }
  
  def sendRequestRepeatly = {
    println("-------------nodes will begin sending msgs------------")
    val system = context.system
    import system.dispatcher
    timer = context.system.scheduler.schedule(1000 milliseconds, requestInterval, self, SendRequest)
  }
  
  def sendRequest = {
    for(node <- actorpool){
       val requestKey = strtoNodeId(randomStrs(requestNum%randomStrs.length) + node.toString)
       node ! Request(requestKey, 0)
    }
  }
  def nodeSendRequest = {
    println("---------nodes start sending msg----------")
    var localnode = actorpool(10)
    for(i <- 0 until requestSum){
        //generate a random request.
      val requestKey = strtoNodeId(randomStrs(i%randomStrs.length) + localnode.toString)
//      val requestKey = strtoNodeId(actorpool(i).toString)
      localnode ! Request( requestKey, 0)
    }
  }
  
  def strtoNodeId(node: String): String = {
    return SHA1.getHexString(node.toString, byteLength)
  }
  
  def addNode = {
    if( actorpool.size == 0){
    	var node = context.system.actorOf(Props(classOf[Node], base, maxBitLength, null, self))
        actorpool += node;
    }else{
      //when adding a new node X to pastry network, select a random node as X's known node A.
      val randomIndex = Random.nextInt(actorpool.size)
      var node = context.system.actorOf(Props(classOf[Node], base, maxBitLength, actorpool(randomIndex),self))
      actorpool += node;
    }
  }
  def terminate = {
   // println("worker monitor " + context.system)
    println("=====result: the average hop num is " + hopSum +"/" +msgSum+" = " +  (hopSum.toDouble/msgSum) )
    context stop self
    context.system.shutdown
  }
  def receive = {
    case ShutDown =>
      terminate
    case NodeInitDone=>
      nodesInNetwork += 1
      if( networkSetDone && nodesInNetwork == actorpool.length){
        println("pastry network init done...there are " + nodesInNetwork + " nodes in the network")
        sendRequestRepeatly
      }
    case SendRequest=>
      sendRequest
      requestNum += 1
      println("each node has send " + requestNum +  "/" + requestSum + " msgs")
      if( requestNum >= requestSum)
        timer.cancel
    case MsgDelivered(sum:Int)=>
//      println("a msg takes " + sum + " hops")
      hopSum += sum
      msgSum += 1
      if(msgSum >= requestSum * actorpool.length)
        terminate
    case NodeShutDown=>
      actorpool -= sender
    case AddNode=>
      addNode
      if(actorpool.size > nodesNum - 1){
        networkSetDone = true
        timer.cancel
      }
  }
}
