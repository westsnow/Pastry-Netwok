package main.scala

import scala.collection.mutable.ArrayBuffer
import akka.actor._
import akka.actor.Props
import com.typesafe.config.ConfigFactory


object main {
  def main(args: Array[String]): Unit = {
    val base = 4;//base is 2^b = 16
    val maxBitLength = 32; // N = 2^32. there are at most 2^32 nodes in pastry network
    val routeTableRowNum = maxBitLength/base; // 8
    val routeTableColNum:Int = (Math.pow(base, 2) - 1).toInt; //15
    
    var nodesNum = 200;
    var requestNum = 5;
    
    
    if(args.length<2){
      println("error:two parameters are required---nodeNum and requestNum")
      return;
    }
    else{
      nodesNum = args(0).toInt
      requestNum = args(1).toInt
    }
    
    
    val actorsystem = ActorSystem("actorsystem", ConfigFactory.load(ConfigFactory.parseString("""
  akka {
    log-dead-letters = off
  }
  """)))
  
  	val nodeMonitor = actorsystem.actorOf(Props(classOf[NodeMonitor], base, maxBitLength,nodesNum, requestNum))
  
   
 } 
}