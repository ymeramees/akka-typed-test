package part4faulttolerance

import akka.actor.typed._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import java.io.File
import scala.concurrent.duration._
import scala.io.Source
import scala.language.postfixOps

object BackoffSupervisorPattern extends App {

  sealed trait Command

  case object ReadFile extends Command

  def commonReceive(context: ActorContext[Command]): Behavior[Command] = Behaviors.receiveMessage[Command] {
    case ReadFile =>
      val dataSource = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
      val data = dataSource.getLines().toList
      context.log.info("I've just read some IMPORTANT data: " + data)
      Behaviors.same
  }.receiveSignal {
    case (_, PostStop) =>
      context.log.warn("Persistent actor has stopped")
      Behaviors.same
    case (_, PreRestart) =>
      context.log.warn("Persistent actor restarting")
      Behaviors.same
  }

  object FileBasedPersistentActor {
    def apply(): Behavior[Command] = Behaviors.setup { context =>
      context.log.info("Persistent actor starting")

      commonReceive(context)
    }
  }

  // Default behavior is to stop the failed actor
//  val simpleActorSystem = ActorSystem(FileBasedPersistentActor(), "simpleActor")
//  simpleActorSystem ! ReadFile

  object SupervisorFBPActor {
    def apply(supervisorStrategy: SupervisorStrategy): Behavior[Command] =
      Behaviors.supervise[Command] {
        Behaviors.setup { context =>

          context.log.info("Persistent actor starting")
          commonReceive(context)
        }
      }.onFailure(supervisorStrategy)
  }

  // Restart the failed actor immediately
//  val supervisorActorSystem = ActorSystem(SupervisorFBPActor(SupervisorStrategy.restart), "supervisorActor")
//  supervisorActorSystem ! ReadFile

  val simpleBackoffSupervisorStrategy = SupervisorStrategy.restartWithBackoff(
    3 seconds, // then 6s, 12s, 24s
    30 seconds,
    0.2
  )

  // Restart with a delay, based on backoff
//  val simpleBackoffSupervisor = ActorSystem(SupervisorFBPActor(simpleBackoffSupervisorStrategy), "simpleBackoffSupervisor")
//  simpleBackoffSupervisor ! ReadFile

  // TODO There seems to be no .onStop in Akka Typed and this basically defines default strategy explicitly
  val stopSupervisorStrategy = SupervisorStrategy.stop

//  val stopBackoffSupervisor = ActorSystem(SupervisorFBPActor(stopSupervisorStrategy), "stopSupervisor")
//  stopBackoffSupervisor ! ReadFile

//  val simpleBackoffSupervisorWithMaxRestarts = ActorSystem( // TODO perhaps the name is too long?
//    SupervisorFBPActor(simpleBackoffSupervisorStrategy.withMaxRestarts(5)), "simpleSupervisorMaxRestarts")
//  simpleBackoffSupervisorWithMaxRestarts ! ReadFile

  object EagerFBPActor {
    def apply(supervisorStrategy: SupervisorStrategy): Behavior[Command] =
      Behaviors.supervise[Command] {
        Behaviors.setup { context =>

          context.log.info("Eager actor starting")

          Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
          commonReceive(context)
        }
      }.onFailure(supervisorStrategy)
  }

//  val eagerSupervisorActorSystem = ActorSystem(EagerFBPActor(SupervisorStrategy.restart), "eagerSupervisor")
//  eagerSupervisorActorSystem ! ReadFile

  val eagerBackoffSupervisorActorSystem = ActorSystem(EagerFBPActor(simpleBackoffSupervisorStrategy), "eagerBackoffSupervisor")

  // TODO How about SupervisorStrategy.restart.withLimit and SupervisorStrategy.resume, should those be included also?
}
