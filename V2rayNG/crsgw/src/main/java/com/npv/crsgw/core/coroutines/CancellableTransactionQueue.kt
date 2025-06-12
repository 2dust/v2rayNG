package com.npv.crsgw.core.coroutines

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CancellableTransactionQueue {

    private val taskQueue: MutableList<suspend CoroutineScope.() -> Unit> = mutableListOf()
    private var currentJob: Job? = null
    private var isRunning = AtomicBoolean(false)
    private val queueScope = CoroutineScope(Dispatchers.Default)
    private var TAG = "CancellableTransactionQueue"

    // 添加任务到队列
    @Synchronized
    fun enqueue(task: suspend CoroutineScope.() -> Unit) {
        taskQueue.add(task)
        if (!isRunning()) {
            processQueue()
        }
    }

    // 按顺序处理任务队列
    @Synchronized
    private fun processQueue() {
        if (isRunning()) {
            println("processQueue isRunning")
            return
        } // 防止重复启动
        if (taskQueue.isEmpty()) {
            println("taskQueue isEmpty")
            isRunning.set(false)
            return
        }

        isRunning.set(true)
        val task = try {
            taskQueue.removeAt(0)
        } catch (e: Exception) {
            println("get Task failed. e = ${e.message}")
            Log.e(TAG, "get Task failed. e = ${e.message}")
            cancelAll()
            return
        }

        // 启动协程处理任务
        currentJob = queueScope.launch {
            try {
                task() // 执行任务
            } catch (e: Exception) {
                println("Task cancelled.")
                Log.e(TAG, "Exception. e = ${e.message} \n  e.stackTrace = $e")
            } finally {
                isRunning.set(false)
                println("next queue")
                processQueue() // 处理下一个任务
            }
        }
    }

    // 取消当前任务和队列
    fun cancelAll() {
        currentJob?.cancel() // 取消当前正在执行的任务
        taskQueue.clear() // 清空剩余任务
        isRunning.set(false)
    }

    // 检查队列是否正在运行
    fun isRunning(): Boolean = isRunning.get()
}