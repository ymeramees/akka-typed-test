package part2actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import part2actors.ChildActors.NaiveBankAccount.AccountCommand

object ChildActors extends App {

  // Actors can create other actors

  object Parent {
    sealed trait Command

    case class CreateChild(name: String) extends Command

    case class TellChild(message: String) extends Command

    def apply(): Behavior[Command] =
      Behaviors.receive { (context, message) =>
        message match {
          case CreateChild(name) =>
            println(s"${context.self.path} creating child")
            // create a new actor right HERE
            val childRef = context.spawn(Child(), name)
            withChild(childRef)
        }
      }

    def withChild(childRef: ActorRef[Command]): Behavior[Command] = Behaviors.receive { (_, message) =>
      message match {
        case TellChild(_) => childRef tell message
          Behaviors.same
      }
    }
  }

  object Child {

    import Parent._

    def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
      println(s"${context.self.path} I got: $message")
      Behaviors.same
    }
  }

  import Parent._

  val system = ActorSystem[Command](Parent(), "ParentChildDemo")
  system ! CreateChild("child")
  system ! TellChild("hey Kid!")

  // actor hierarchies
  // parent -> child -> grandChild
  //        -> child2 ->

  /*
    Guardian actors (top-level)
    - /system = system guardian
    - /user = user-level guardian
    - / = the root guardian
   */

  /**
   * Danger!
   *
   * NEVER PASS MUTABLE ACTOR STATE, OR THE `THIS` REFERENCE, TO CHILD ACTORS.
   *
   * NEVER IN YOUR LIFE.
   */


  object NaiveBankAccount {
    sealed trait AccountCommand

    case class Deposit(amount: Int) extends AccountCommand

    case class Withdraw(amount: Int) extends AccountCommand

    case object InitializeAccount extends AccountCommand

    case object CheckCardStatuses extends AccountCommand

    def apply(): Behavior[AccountCommand] = Behaviors.setup { context =>
      new NaiveBankAccount(context)
    }
  }

  class NaiveBankAccount(context: ActorContext[AccountCommand]) extends AbstractBehavior[AccountCommand](context) {

    import CreditCard._
    import NaiveBankAccount._

    var amount = 0

    override def onMessage(message: AccountCommand): Behavior[AccountCommand] =
      message match {
        case InitializeAccount =>
          val creditCardRef = context.spawn(CreditCard(), "card")
          creditCardRef ! AttachToAccount(this) // !!
          initialized(creditCardRef)
      }

    def initialized(creditCard: ActorRef[CreditCardCommand]): Behavior[AccountCommand] = Behaviors.receive { (_, message) =>
      message match {
        case Deposit(funds) => deposit(funds)
          Behaviors.same
        case Withdraw(funds) => withdraw(funds)
          Behaviors.same
        case CheckCardStatuses =>
          creditCard ! CheckStatus
          Behaviors.same
      }
    }

    def deposit(funds: Int) = {
      println(s"${context.self.path} depositing $funds on top of $amount")
      amount += funds
    }

    def withdraw(funds: Int) = {
      println(s"${context.self.path} withdrawing $funds from $amount")
      amount -= funds
    }
  }

  object CreditCard {

    sealed trait CreditCardCommand

    case class AttachToAccount(bankAccount: NaiveBankAccount) extends CreditCardCommand // !!

    case object CheckStatus extends CreditCardCommand

    val CreditCardKey = ServiceKey[CreditCardCommand]("creditCard")

    def apply(): Behavior[CreditCardCommand] = Behaviors.receive { (context, message) =>
      message match {
        case AttachToAccount(account) =>
          Receptionist.register(CreditCardKey, context.self)
          attachedTo(account)
      }
    }

    def attachedTo(account: NaiveBankAccount): Behavior[CreditCardCommand] = Behaviors.receive { (context, message) =>
      message match {
        case CheckStatus =>
          println(s"${context.self.path} your message has been processed.")
          // benign
          account.withdraw(1) // because I can
          Behaviors.same
      }
    }
  }

  import NaiveBankAccount._

  val bankAccountRef = ActorSystem(NaiveBankAccount(), "account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  bankAccountRef ! CheckCardStatuses

  // WRONG!!!!!!
}