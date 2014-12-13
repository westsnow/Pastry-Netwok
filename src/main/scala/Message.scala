package main.scala

import akka.actor.ActorRef




case class join(nodeidref: NodeIdWithRef, time:Int)
//A,B...Z tell X to update its row i

//if state = 0, its just a msg from a normal node;
//if state = 1, its a msg from the last node on the join path. Then the new node received all route tables through the path
//if state = 2, its an exception msg, ie: a node in the pastry network has the same nodeid with the new node. Then the new
//  shutdown itself
case class routeTableMsgToX(routeTable:Array[Array[NodeIdWithRef]], num:Int, state:Int)




case class routeTableMsg(routeTable:Array[Array[NodeIdWithRef]])

//monitor sends a msg to a node asking it to make a request to a node with reference nodeRef
case class Request(nodeid:String, hop:Int)
case object shutdown
case object NodeInitDone
case object NodeShutDown

case class MsgDelivered(hopSum:Int)