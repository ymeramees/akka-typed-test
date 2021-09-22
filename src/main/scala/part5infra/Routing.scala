package part5infra

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}

object Routing extends App {

  /**
   * #1 - manual router // TODO doesn't exist in Typed?
   */

  /**
   * Method #1 - a router actor with its own children
   * POOL router
   */
  // 1.1 programmatically (in code)
  val pool = Routers.pool(5) {
    // Otherwise the slaves are stopped and when the last one is stopped, pool itself is also stopped
    Behaviors.supervise(Slave()).onFailure[Exception](SupervisorStrategy.restart)
  }.withRoundRobinRouting() // Optional, as this is default for pool routers

  object Slave {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(message)
      Behaviors.same
    }
  }

//  val system = ActorSystem(pool, "routersDemo")

//  for (i <- 1 to 10) {
//    system ! s"[$i] Hello from the world"
//  }

  // 1.2 from configuration // TODO routers from config seem to be not available in Typed

  /**
   * Method #2 - router with actors created elsewhere
   * GROUP router
   */

  // 2.1 in the code
  object GroupMaster {
    val groupKey = ServiceKey[String]("slavesGroup")

    def apply(): Behavior[String] = Behaviors.setup { context =>
      // .. in another part of my application
      (1 to 5).foreach { i =>
        val slave = context.spawn(Slave(), s"slave_$i")
        context.system.receptionist ! Receptionist.register(groupKey, slave)
      }

      val slavesGroup = Routers.group(groupKey).withRoundRobinRouting() // Default is randomRouting for group pool routers
      val groupRouter = context.spawn(slavesGroup, "slaves-group")

      Behaviors.receive[String] { (_, message) =>
        message match {
          case message =>
            groupRouter ! message
            Behaviors.same
        }
      }
    }
  }

//  val groupMaster = ActorSystem(GroupMaster(), "groupPoolMaster")
//  for (i <- 1 to 10) {
//    groupMaster ! s"[$i] Hello from the world"
//  }

  // 2.2 from configuration // TODO routers from config seem to be not available in Typed

  /**
   * Special messages
   */
  sealed trait Message

  final case class BroadCastMsg(message: String) extends Message

  final case class NormalMessage(message: String) extends Message

  object SlaveWithBroadcast {
    def apply(): Behavior[Message] = Behaviors.receive { (context, message) =>
      context.log.info(message.toString)
      Behaviors.same
    }
  }

  val poolWithBroadcast = Routers.pool(5) {
    Behaviors.supervise(SlaveWithBroadcast()).onFailure[Exception](SupervisorStrategy.restart)
  }.withBroadcastPredicate(_.isInstanceOf[BroadCastMsg])

  val systemWithBroadcast = ActorSystem(poolWithBroadcast, "broadcastDemo")
  systemWithBroadcast ! BroadCastMsg("hello, everyone")
}
