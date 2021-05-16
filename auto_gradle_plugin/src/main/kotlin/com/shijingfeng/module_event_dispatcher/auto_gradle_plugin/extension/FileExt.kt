/** 生成的 Java 类名 */
@file:JvmName("FileExt")
package com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.extension

import java.io.File

/**
 * Function: File 相关扩展函数
 * Date: 2020/12/13 14:45
 * Description:
 * Author: ShiJingFeng
 */

/**
 * 递归遍历文件和目录
 */
internal fun File.recurseTraverse(block: (File) -> Unit) {
    recurse(this, block)
}

/**
 * 递归遍历文件和目录
 */
private fun recurse(file: File, block: (File) -> Unit) {
    if (file.isFile) {
        block(file)
        return
    }

    val childFileList = file.listFiles() ?: arrayOf()

    if (childFileList.isEmpty()) {
        block(file)
        return
    }
    childFileList.forEach { childFile ->
        recurse(childFile, block)
    }
}