package chat.tox.antox.utils

//import chat.tox.antox.R
import im.tox.tox4j.core.enums.ToxUserStatus

object IconColor {

  def iconDrawable(isOnline: Boolean, status: ToxUserStatus): Int = {
    val color = if (!isOnline) {
     0
    } else if (status == ToxUserStatus.NONE) {
      0
    } else if (status == ToxUserStatus.AWAY) {
     0
    } else if (status == ToxUserStatus.BUSY) {
     0
    } else {
      0
    }
    color
  }

}
