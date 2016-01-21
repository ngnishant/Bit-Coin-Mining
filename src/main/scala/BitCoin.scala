import java.security.MessageDigest
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import akka.actor._
import akka.actor.Actor
import akka.actor.ActorDSL._
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.routing.RoundRobinRouter


/*to handle the startmining message from the master*/
case class startMining_Worker(zeros: Integer)

/*to store the bitcoins received from the workers after each worker unit 
  is processed and display the total number of bitcoins mined*/
case class minedBitCoins(bitc:ArrayBuffer[String])

/*to start 12 workers within the same host*/
case class startMining_Master(zeros:Integer)

/*to print all bitcoins*/
case class printallbitcoins(totalbitc:ArrayBuffer[String])

/* to handle remote actors calls*/
case class remote()

/*   gatorlink id + random string of length 10 is hashed with SHA-256
     hash is compared for number of zeros and saved in an ArrayBuffer.
     The mined bitcoins are passed to the master after a 10 bitcoins are generated 
     or the program exceeds 30 seconds
  */
class Worker extends Actor {
/*Worker used only for the Server machine*/
  def receive = {
      case startMining_Worker(zeros:Integer) => {
      //println("test1")
        var bitcoins:ArrayBuffer[String]= ArrayBuffer[String]() /*String array to  store the bitcoins*/
        var count_bitcoins:Integer=0
        val start=System.currentTimeMillis()
        //println("test2")
        while(start+300000>=System.currentTimeMillis()){
/*letting the worker execute for 5 mins*/
          //println("While1")
          var random_string:String=Random.alphanumeric.take(10).mkString
          var s:String = "ngnishant;"+random_string+count_bitcoins
          //println(s)
          var sha = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
          var bitcoin:String=sha.map("%02x".format(_)).mkString
          //println(bitcoin)
          var leading_zeros_in_hash:String=bitcoin.substring(0,zeros)
          var expected_zeros:String="0"*zeros
           //println(bitcoin)
          if(leading_zeros_in_hash.equals(expected_zeros)){
	   /*comparing the leading zeros of bitcoin with user input*/
           println("ngnishant;"+random_string+count_bitcoins+" "+bitcoin)
           bitcoins+="serverngnishant;"+random_string+count_bitcoins+" "+bitcoin 
/*appending tag server before the bitcoin to differentiate between server and client bitcoins*/
           count_bitcoins+=1
          }
        }
        sender ! minedBitCoins(bitcoins)
       }

  }
}

class Worker_Client extends Actor {
  def receive = {
/*Worker used only for the Client machine*/
      case startMining_Worker(zeros:Integer) => {
      //println("test1")
        var bitcoins:ArrayBuffer[String]= ArrayBuffer[String]()
        var count_bitcoins:Integer=0
        val start=System.currentTimeMillis()
        //println("test2")
        while(start+300000>=System.currentTimeMillis()){
/*letting the worker execute for 5 mins*/
          //println("While1")
          var random_string:String=Random.alphanumeric.take(5).mkString
          var s:String = "ngnishant;"+random_string+count_bitcoins
          //println(s)
          var sha = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
          var bitcoin:String=sha.map("%02x".format(_)).mkString
          //println(bitcoin)
          var leading_zeros_in_hash:String=bitcoin.substring(0,zeros)
          var expected_zeros:String="0"*zeros
           //println(bitcoin)
          if(leading_zeros_in_hash.equals(expected_zeros)){
           println("ngnishant;"+random_string+count_bitcoins+" "+bitcoin)
           bitcoins+="clientngnishant;"+random_string+count_bitcoins+" "+bitcoin
/*appending tag server before the bitcoin to differentiate between server and client bitcoins*/
           count_bitcoins+=1
          }
        }
        sender ! minedBitCoins(bitcoins)
       }
      /* to connect with the remote master using the received ip address */
      case ipaddress:String => {
      /* to connect with the remote master using the received ip address */
        println("remote worker initialized")
        val master=context.actorSelection("akka.tcp://master@"+ipaddress+":5150/user/Master")
        master ! remote()
      }
  }
}


class Master extends Actor {
  private var leadingzeros:Integer=0
  private var workernumber:Integer=0
  private var total_bitcoins:ArrayBuffer[String]=  ArrayBuffer[String]()
  def receive = {
     /*to start 6 workers within the same host */
     case startMining_Master(zeros:Integer) => {
         leadingzeros=zeros
         val worker =context.actorOf(Props[Worker].withRouter(RoundRobinRouter(nrOfInstances= 6)))
         for (n <- 1 to 6){
           //println("calling worker "+n) 
           worker ! startMining_Worker(zeros)}
         }
     /* to receive bitcoins, save and print*/
     case minedBitCoins(bitc:ArrayBuffer[String]) => {
         total_bitcoins++=bitc
         workernumber+=1
         if(workernumber==12){
/*checking if all the workers have replied*/
           self ! printallbitcoins(total_bitcoins)
           context.system.shutdown()           
         }
       }
     case printallbitcoins(totalbitc:ArrayBuffer[String]) => {
       var bitcoins_server:ArrayBuffer[String]= ArrayBuffer[String]()
       var bitcoins_client:ArrayBuffer[String]= ArrayBuffer[String]()	
       println("Total BitCoins mined: "+totalbitc.length)
       for(i<-0 until totalbitc.length){
       if(totalbitc(i).substring(0,6).equals("server")){bitcoins_server+=totalbitc(i)}
       if(totalbitc(i).substring(0,6).equals("client")){bitcoins_client+=totalbitc(i)}  
       }
       println("The bitcoins mined by server are: ")
       if(bitcoins_server.length==0){println("Server could not mine bitcoins in the given time.")}
       for(j<-0 until bitcoins_server.length){println((j+1)+" "+bitcoins_server(j).substring(6,bitcoins_server(j).length))} 
       println("The bitcoins mined by client are: ")
       if(bitcoins_client.length==0){println("Client could not mine bitcoins in the given time.")}
       for(j<-0 until bitcoins_client.length){println((j+1)+" "+bitcoins_client(j).substring(6,bitcoins_client(j).length))} 
     }
    /* to start 6 workers in the remote host*/ 
      case remote() => {
         println("starting remote worker")
         sender ! startMining_Worker(leadingzeros)
       }
    }
}


object BitCoin extends App {
  var input:String=args(0)
  if(input.forall(_.isDigit))
  {
    var zeros=input.toInt
    val system = ActorSystem("master")
    val master = system.actorOf(Props[Master],name="Master")
    println("calling master")
    master ! startMining_Master(zeros)
  }
  else
  {
    val remotesystem =ActorSystem("RemoteSystem")
    val remoteworker = remotesystem.actorOf(Props[Worker_Client].withRouter(RoundRobinRouter(nrOfInstances= 6)))
    for (n <- 1 to 6){
      println("calling remote worker "+n+"")
        remoteworker ! input}
  }
}
