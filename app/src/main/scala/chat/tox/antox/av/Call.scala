package chat.tox.antox.av

import chat.tox.antox.tox.ToxSingleton
import chat.tox.antox.utils.AudioCapture
import chat.tox.antox.wrapper.{ContactKey, CallNumber}
import im.tox.tox4j.av.enums.{ToxavCallControl, ToxavFriendCallState}
import im.tox.tox4j.exceptions.ToxException
import rx.lang.scala.subjects.BehaviorSubject

class Call(val callNumber: CallNumber, val contactKey: ContactKey) {

  private var friendState: Set[ToxavFriendCallState] = Set()
  val friendStateSubject = BehaviorSubject[Set[ToxavFriendCallState]](friendState)

  private var selfState = SelfCallState.DEFAULT

  //only for outgoing audio
  private val sampleRate = 48000 //in Hz
  private val audioLength = 20 //in ms
  private val channels = 2

  val ringing = BehaviorSubject[Boolean](false)
  var incoming = false

  var startTime: Long = 0
  def duration: Long = System.currentTimeMillis() - startTime //in milliseconds

  def active: Boolean = !friendState.contains(ToxavFriendCallState.FINISHED)
  def onHold: Boolean = friendState.isEmpty

  val audioCapture: AudioCapture = new AudioCapture(sampleRate, channels)
  val audioPlayer = new AudioPlayer(sampleRate, channels)

  private def frameSize = (sampleRate * audioLength) / 1000

  friendStateSubject.subscribe(_ => {
    if (active) {
      ringing.onNext(false)
    }
  })

  def startCall(audioBitRate: Int, videoBitRate: Int): Unit = {
    ToxSingleton.toxAv.call(callNumber.number, audioBitRate, videoBitRate)
    selfState = selfState.copy(audioBitRate = audioBitRate, videoBitRate = videoBitRate)
    incoming = false
    ringing.onNext(true)
  }

  def answerCall(receivingAudio: Boolean, receivingVideo: Boolean): Unit = {
    ToxSingleton.toxAv.answer(callNumber.number, selfState.audioBitRate, selfState.videoBitRate)
    callStarted(selfState.audioBitRate, selfState.videoBitRate)
    ringing.onNext(false)
  }

  def onIncoming(receivingAudio: Boolean, receivingVideo: Boolean): Unit = {
    incoming = true
    ringing.onNext(true)
    selfState = selfState.copy(receivingAudio = receivingAudio, receivingVideo = receivingVideo)
  }

  def updateFriendState(state: Set[ToxavFriendCallState]): Unit = {
    friendState = state
    friendStateSubject.onNext(friendState)
  }

  private def callStarted(audioBitRate: Int, videoBitRate: Int): Unit = {
    startTime = System.currentTimeMillis()

    new Thread(new Runnable {
      override def run(): Unit = {
        audioCapture.start()

        while (active) {
          val start = System.nanoTime()
          if (selfState.sendingAudio) {
            try {
              ToxSingleton.toxAv.audioSendFrame(callNumber.number,
                audioCapture.readAudio(frameSize, channels),
                frameSize, channels, sampleRate)
            } catch {
              case e: ToxException[_] =>
                end(error = true)
            }
          }

          val timeTaken = System.nanoTime() - start
          if (timeTaken < audioLength)
            Thread.sleep(audioLength - (timeTaken / 10^6))
        }
      }
    }).start()

    audioPlayer.start()
  }

  def onAudioFrame(pcm: Array[Short], channels: Int, sampleRate: Int): Unit = {
    audioPlayer.bufferAudioFrame(pcm, channels, sampleRate)
  }

  def muteSelfAudio(): Unit = {
    selfState = selfState.copy(audioMuted = true)
    ToxSingleton.toxAv.setAudioBitRate(callNumber.number, 0)
    audioCapture.stop()
  }

  def unmuteSelfAudio(): Unit = {
    selfState = selfState.copy(audioMuted = false)
    ToxSingleton.toxAv.setAudioBitRate(callNumber.number, selfState.audioBitRate)
    audioCapture.start()
  }

  def hideSelfVideo(): Unit = {
    selfState = selfState.copy(videoHidden = true)
  }

  def showSelfVideo(): Unit = {
    selfState = selfState.copy(videoHidden = false)
    //TODO
  }

  def muteFriendAudio(): Unit = {
    ToxSingleton.toxAv.callControl(callNumber.number, ToxavCallControl.MUTE_AUDIO)
  }

  def unmuteFriendAudio(): Unit = {
    ToxSingleton.toxAv.callControl(callNumber.number, ToxavCallControl.UNMUTE_AUDIO)
  }

  def hideFriendVideo(): Unit = {
    ToxSingleton.toxAv.callControl(callNumber.number, ToxavCallControl.HIDE_VIDEO)
  }

  def showFriendVideo(): Unit = {
    ToxSingleton.toxAv.callControl(callNumber.number, ToxavCallControl.SHOW_VIDEO)
  }

  def end(error: Boolean = false): Unit = {
    // only send a call control if the call wasn't ended unexpectedly
    if (!error) {
      ToxSingleton.toxAv.callControl(callNumber.number, ToxavCallControl.CANCEL)
    }

    audioCapture.stop()
    cleanUp()
  }

  private def cleanUp(): Unit = {
    audioPlayer.cleanUp()
    audioCapture.cleanUp()
  }
}