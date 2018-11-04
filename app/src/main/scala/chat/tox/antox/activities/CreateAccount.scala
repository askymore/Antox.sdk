package chat.tox.antox.activities

import java.io.File
import java.util.regex.Pattern

import android.content.{Context, Intent}
import android.preference.PreferenceManager
import android.widget._
import chat.tox.antox.data.{State, UserDB}
import chat.tox.antox.tox.{ToxDataFile, ToxService}
import chat.tox.antox.toxme.ToxMe.PrivacyLevel
import chat.tox.antox.toxme.ToxMeError.ToxMeError
import chat.tox.antox.toxme.{ToxData, ToxMe, ToxMeError, ToxMeName}
import chat.tox.antox.utils._
import chat.tox.antox.wrapper.ToxAddress
import im.tox.tox4j.core.exceptions.ToxNewException
import im.tox.tox4j.core.options.SaveDataOptions.ToxSave
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.exceptions.ToxException
import im.tox.tox4j.impl.jni.ToxCoreImpl
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.AndroidMainThreadScheduler



class CreateAccount(ctx:Context)  {









  def validAccountName(account: String): Boolean = {
    val pattern = Pattern.compile("\\s")
    val pattern2 = Pattern.compile(File.separator)
    var matcher = pattern.matcher(account)
    val containsSpaces = matcher.find()
    matcher = pattern2.matcher(account)
    val containsFileSeparator = matcher.find()

    !(account == "" || containsSpaces || containsFileSeparator)
  }


  def loginAndStartMain(accountName: String, password: String) {
    val userDb = State.userDb(ctx)
    State.login(accountName, ctx)
    userDb.updateActiveUserDetail(DatabaseConstants.COLUMN_NAME_PASSWORD, password)

    // Start the activity
    val startTox = new Intent(ctx, classOf[ToxService])
    ctx.startService(startTox)

  }

  def createToxData(accountName: String): ToxData = {
    val toxData = new ToxData
    val toxOptions = new ToxOptions(Options.ipv6Enabled, Options.udpEnabled)
    val tox = new ToxCoreImpl(toxOptions)
    val toxDataFile = new ToxDataFile(ctx, accountName)
    toxDataFile.saveFile(tox.getSavedata)
    toxData.address = new ToxAddress(tox.getAddress.value)
    toxData.fileBytes = toxDataFile.loadFile()
    toxData
  }

  def loadToxData(fileName: String): Option[ToxData] = {
    val toxData = new ToxData
    val toxDataFile = new ToxDataFile(ctx, fileName)

    val toxOptions = new ToxOptions(
      Options.ipv6Enabled,
      Options.udpEnabled,
      saveData = ToxSave(toxDataFile.loadFile()))

    try {
      val tox = new ToxCoreImpl(toxOptions)
      toxData.address = new ToxAddress(tox.getAddress.value)
      toxData.fileBytes = toxDataFile.loadFile()
      Option(toxData)
    } catch {
      case error: ToxNewException =>
        if (error.code == ToxNewException.Code.LOAD_ENCRYPTED) {
          Toast.makeText(
            ctx,
            "error",
            Toast.LENGTH_SHORT
          ).show()
        } else {
          Toast.makeText(
            ctx,
            "error",
            Toast.LENGTH_SHORT
          ).show()
        }

        None
    }
  }



  def createAccount(rawAccountName: String, userDb: UserDB, shouldCreateDataFile: Boolean, shouldRegister: Boolean): Unit = {
    val toxMeName = ToxMeName.fromString(rawAccountName, shouldRegister)
    if (!validAccountName(toxMeName.username)) {
//      showBadAccountNameError()
    } else if (userDb.doesUserExist(toxMeName.username)) {
      val context = ctx.getApplicationContext
      val text = "dududud"    //todo
      val duration = Toast.LENGTH_LONG
      val toast = Toast.makeText(context, text, duration)
      toast.show()
    } else {
//      disableRegisterButton()

      val toxData =
        if (shouldCreateDataFile) {
          // Create tox data save file
          try {
            Some(createToxData(toxMeName.username))
          } catch {
            case e: ToxException[_] =>
              AntoxLog.debug("Failed creating tox data save file")
              None
          }
        } else {
          loadToxData(toxMeName.username)
        }

      toxData match {
        case Some(data) =>
          val observable =
            if (shouldRegister) {
              // Register acccount
              if (ConnectionManager.isNetworkAvailable(ctx)) {
                val proxy = ProxyUtils.netProxyFromPreferences(PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext))
                ToxMe.registerAccount(toxMeName, PrivacyLevel.PUBLIC, data, proxy)
              } else {
                // fail if there is no connection
                Observable.just(Left(ToxMeError.CONNECTION_ERROR))
              }

            } else {
              //succeed with empty password
              Observable.just(Right(""))
            }

          observable
            .observeOn(AndroidMainThreadScheduler())
            .subscribe(result => {
              onRegistrationResult(toxMeName, data, result)
            }, error => {
              AntoxLog.debug("Unexpected error registering account.")
              error.printStackTrace()
            })

        case None =>
//          enableRegisterButton()
      }
    }
  }

  def onRegistrationResult(toxMeName: ToxMeName, toxData: ToxData, result: Either[ToxMeError, String]): Unit = {
    var successful = true
    var accountPassword = ""

    if (successful) {
      State.userDb(ctx).addUser(toxMeName, toxData.address, "")
      loginAndStartMain(toxMeName.username, accountPassword)
    }
     else {
      }

  }

  def onClickRegisterAccount(ss: String) {
//    val accountField = findViewById(R.id.create_account_name).asInstanceOf[EditText]
    val account = ss
    val userDb = State.userDb(ctx)
    val shouldRegister = false
    createAccount(account, userDb, shouldCreateDataFile = true, shouldRegister)
  }

}