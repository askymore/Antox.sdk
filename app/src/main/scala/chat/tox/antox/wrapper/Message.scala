package chat.tox.antox.wrapper

import java.sql.Timestamp

import android.content.Context
//import chat.tox.antox.R
import chat.tox.antox.data.CallEventKind
import chat.tox.antox.tox.ToxSingleton
import chat.tox.antox.utils.{FileUtils, TimestampUtils}
import chat.tox.antox.wrapper.MessageType.MessageType

case class Message(id: Int,
                   messageId: Int,
                   key: ContactKey,
                   senderKey: ToxKey,
                   senderName: String,
                   message: String,
                   received: Boolean,
                   read: Boolean,
                   sent: Boolean,
                   timestamp: Timestamp,
                   size: Int,
                   `type`: MessageType,
                   fileKind: FileKind,
                   callEventKind: CallEventKind) {

  def logFormat(): Option[String] =
    if (!isFileTransfer) {
      val name =
        if (isMine) {
          ToxSingleton.tox.getName
        } else {
          senderName
        }

      val prettyTimestamp = TimestampUtils.prettyTimestamp(timestamp, isChat = true)

      Some(s"<$name> $message [$prettyTimestamp]")
    } else {
      None
    }

  def toNotificationFormat(context: Context): String = {
    `type` match {
      case MessageType.ACTION | MessageType.GROUP_ACTION =>
        s"$senderName $message"
      case MessageType.FILE_TRANSFER =>
        val fileName = message

        if (FileUtils.hasImageFilename(fileName)) {
          if (isMine) {
            context.getResources.getString(0)
          } else {
            context.getResources.getString(0)
          }
        } else {
          if (isMine) {
            context.getResources.getString(0)
          } else {
            context.getResources.getString(0)
          }
        }
      case MessageType.GROUP_MESSAGE =>
        s"$senderName: $message"

      case _ =>
        message
    }
  }

  def isMine: Boolean = {
    senderKey.equals(ToxSingleton.tox.getSelfKey)
  }

  def isFileTransfer: Boolean = {
    MessageType.transferValues.contains(`type`)
  }
}
