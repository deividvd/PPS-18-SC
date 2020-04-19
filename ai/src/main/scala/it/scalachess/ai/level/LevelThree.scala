package it.scalachess.ai.level

import it.scalachess.core.Color
import it.scalachess.core.board.Board
import it.scalachess.core.logic.moves.FullMove

/**
 * The level Three AI plays TODO
 */
class LevelThree() extends LevelTwo {

  override protected val minimaxDepth: Int = 2

  override def apply(board: Board, aiPlayer: Color, history: Seq[FullMove]): FullMove = {
    verifyGameIsPlayable(board, aiPlayer, history)
    moveWithMaxEval(minimax(board, history, aiPlayer, minimaxDepth, evaluatePiecesInBoard))
  }

}