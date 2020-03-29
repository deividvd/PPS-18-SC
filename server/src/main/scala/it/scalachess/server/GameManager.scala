package it.scalachess.server

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import it.scalachess.core.{ ChessGame, Ongoing, Result, WinByForfeit }
import it.scalachess.core.colors.{ Black, Color, White }
import it.scalachess.server.LobbyManager.Lobby
import it.scalachess.util.NetworkMessages.{
  ClientMessage,
  FailedMove,
  ForfeitGame,
  GameAction,
  GameEnded,
  GameStarted,
  LobbyMessage,
  Move,
  RequestMove
}
import scalaz.{ Failure, Success }
import scala.util.Random

object GameManager {

  def apply(lobby: Lobby, parent: ActorRef[LobbyMessage]): Behavior[GameAction] =
    Behaviors.setup { context =>
      val players = randomizeRoles(lobby)
      val game    = ChessGame.standard()
      players foreach { case (color, p) => p ! GameStarted(color, game, context.self) }
      new GameManager(players, lobby.gameId, context, parent).ongoingGame(game)
    }

  private def randomizeRoles(lobby: Lobby): Map[Color, ActorRef[ClientMessage]] =
    if (Random.nextInt > 0.5) Map(White -> lobby.players._1, Black -> lobby.players._2)
    else Map(White                      -> lobby.players._2, Black -> lobby.players._1)
}

class GameManager private (players: Map[Color, ActorRef[ClientMessage]],
                           gameId: String,
                           context: ActorContext[GameAction],
                           server: ActorRef[LobbyMessage]) {

  private def ongoingGame(game: ChessGame): Behavior[GameAction] =
    Behaviors.receiveMessage {
      case Move(move, player) if playerCanMove(game, player) => ongoingGame(tryMove(move, game))
      case ForfeitGame(player) if colorOf(player).isDefined  => forfeits(player); Behaviors.same
      case _                                                 => failedMove("Client cant move", game); Behaviors.same
    }

  private def tryMove(move: String, game: ChessGame): ChessGame =
    game(move) match {
      case Success(updated) =>
        askToMove(updated)
        updated.gameStatus match {
          case Ongoing   => updated
          case r: Result => communicateResultsAndStop(r); updated
        }
      case Failure(err) => failedMove(err, game); game
    }

  private def failedMove(error: String, game: ChessGame): Unit = {
    val player = players(game.player)
    player ! FailedMove(error)
    player ! RequestMove(game.player, game, context.self)
  }

  private def forfeits(player: ActorRef[ClientMessage]): Unit =
    colorOf(player).fold(()) { c =>
      communicateResultsAndStop(WinByForfeit(c))
    }

  private def askToMove(game: ChessGame): Unit =
    players(game.player) ! RequestMove(game.player, game, context.self)

  private def communicateResultsAndStop(result: Result): Unit = {
    players.values.foreach { _ ! GameEnded(result) }
    server ! LobbyManager.TerminateGame(gameId, result, context.self)
  }

  private def playerCanMove(game: ChessGame, player: ActorRef[ClientMessage]): Boolean =
    colorOf(player).fold(false)(game.player == _)

  private def colorOf(client: ActorRef[ClientMessage]): Option[Color] = //Todo test actorRef equality
    (players filter { case (_, player) => player == client }).keys.headOption

}
