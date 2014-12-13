package main.scala

import akka.actor.ActorRef

class NodeIdWithRef (nodeid:String, ref:ActorRef){
  
  def getnodeId:String ={
    return nodeid
  }
  
  def getRef:ActorRef = {
    return ref
  }
}