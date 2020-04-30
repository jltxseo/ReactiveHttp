package leavesc.reactivehttp.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import leavesc.reactivehttp.core.bean.IHttpResBean
import leavesc.reactivehttp.core.callback.RequestCallback
import leavesc.reactivehttp.core.callback.RequestMultiplyCallback
import leavesc.reactivehttp.core.callback.RequestMultiplyToastCallback
import leavesc.reactivehttp.core.config.HttpConfig
import leavesc.reactivehttp.core.coroutine.ICoroutineEvent
import leavesc.reactivehttp.core.exception.BaseException
import leavesc.reactivehttp.core.exception.LocalBadException
import leavesc.reactivehttp.core.exception.ServerBadException
import leavesc.reactivehttp.core.viewmodel.IUIActionEvent

/**
 * 作者：leavesC
 * 时间：2019/5/31 11:16
 * 描述：
 */
open class BaseRemoteDataSource<T : Any>(private val iActionEvent: IUIActionEvent?, private val serviceApiClass: Class<T>) : ICoroutineEvent {

    protected fun getService(host: String = RetrofitManagement.serverUrl): T {
        return RetrofitManagement.getService(serviceApiClass, host)
    }

    override val lifecycleCoroutineScope: CoroutineScope = iActionEvent?.lifecycleCoroutineScope
            ?: GlobalScope

    protected fun <T> execute(block: suspend () -> IHttpResBean<T>, callback: RequestCallback<T>?, quietly: Boolean = false): Job {
        val temp = true
        return launchIO {
            try {
                if (!temp) {
                    launchUI {
                        showLoading()
                    }
                }
                val response = block()
                callback?.let {
                    if (response.httpIsSuccess) {
                        launchUI {
                            callback.onSuccess(response.httpData)
                        }
                    } else {
                        handleException(ServerBadException(response.httpMsg, response.httpCode), callback)
                    }
                }
            } catch (throwable: Throwable) {
                handleException(throwable, callback)
            } finally {
                if (!temp) {
                    launchUI {
                        dismissLoading()
                    }
                }
            }
        }
    }

    //同步请求，可能会抛出异常，外部需做好捕获异常的准备
    @Throws(BaseException::class)
    protected fun <T> request(block: suspend () -> IHttpResBean<T>): T {
        return runBlocking {
            val asyncIO = asyncIO {
                block()
            }
            try {
                val response = asyncIO.await()
                if (response.httpIsSuccess) {
                    return@runBlocking response.httpData
                }
                throw ServerBadException(response.httpMsg, response.httpCode)
            } catch (throwable: Throwable) {
                throw generateBaseException(throwable)
            }
        }
    }

    private fun generateBaseException(throwable: Throwable): BaseException {
        return if (throwable is BaseException) {
            throwable
        } else {
            LocalBadException(throwable.message
                    ?: "", HttpConfig.CODE_LOCAL_UNKNOWN, throwable)
        }
    }

    private fun <T> handleException(throwable: Throwable, callback: RequestCallback<T>?) {
        callback?.let {
            launchUI {
                val exception = generateBaseException(throwable)
                when (callback) {
                    is RequestMultiplyToastCallback -> {
                        showToast(exception.formatError)
                        callback.onFail(exception)
                    }
                    is RequestMultiplyCallback -> {
                        callback.onFail(exception)
                    }
                    else -> {
                        showToast(exception.formatError)
                    }
                }
            }
        }
    }

    private fun showLoading() {
        iActionEvent?.showLoading()
    }

    private fun dismissLoading() {
        iActionEvent?.dismissLoading()
    }

    private fun showToast(msg: String) {
        iActionEvent?.showToast(msg)
    }

}