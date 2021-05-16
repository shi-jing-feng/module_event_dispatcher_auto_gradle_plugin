package com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.tranform

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_CLASS
import com.android.build.gradle.internal.pipeline.TransformManager.SCOPE_FULL_PROJECT
import com.android.utils.FileUtils
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.extension.recurseTraverse
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.extension.replaceToStandardFileSeparator
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.plugin.ModuleEventDispatcherExt
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util.PrintUtil
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util.Scanner
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util.Weaver
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.Project
import java.io.File

/**
 * Function: 自定义 Transform
 * Date: 2020/12/4 15:01
 * Description:
 * Author: ShiJingFeng
 */
internal class ModuleEventDispatcherTransform(
    project: Project,
    printUtil: PrintUtil
) : Transform() {

    /** Project */
    private val mProject = project
    /** 日志打印工具类 */
    private val mPrintUtil = printUtil
    /** 注解执行器自动生成的类 全限定名称(. 被替换成了 /) Set (因为多渠道打包的原因, 故使用Set而不用List) */
    private val mClassNameSet = mutableSetOf<String>()
    /** 扫描器 */
    private val mScanner = Scanner(
        printUtil = mPrintUtil,
        classNameSet = mClassNameSet
    )
    /** 织入器 */
    private val mWeaver = Weaver(
        printUtil = mPrintUtil,
        classNameSet = mClassNameSet
    )

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        if (transformInvocation == null) {
            return
        }
        // 初始化日志输出工具类
        mProject.extensions.getByType(ModuleEventDispatcherExt::class.java).log.run {
            mPrintUtil.enable = enable
            mPrintUtil.logPrefix = logPrefix
            mPrintUtil.logLevel = logLevel
        }

        // 输入 (上一个插件的输出)
        val inputs = transformInvocation.inputs
        // 输出 (下一个插件的输入)
        val outputProvider = transformInvocation.outputProvider
        // 包括 要织入的类 的该插件的 input文件
        var includedWeaveClassSrc: File? = null
        // 需要包括 要织入的类 的该插件的 output文件
        var includeWeaveClassDest: File? = null
        // 用于计算全部耗时时间
        val allStartTime = System.currentTimeMillis()
        // 用于计算每一项的耗时时间
        var startTime: Long

        // 解决android tool 3.6.0以上的环境，当依赖库的代码发生变化，增量编译会出现 DexArchiveMergerException 的问题
        if (!isIncremental) {
            outputProvider.deleteAll()
        }
        mPrintUtil.print(mPrintUtil.toString())
        inputs.forEach { input ->
            startTime = System.currentTimeMillis()
            mPrintUtil.print("******************** 开始遍历jar包 ********************")
            // 遍历 jar 包 (当前依赖的 库模块 和 第三方库 会生成jar文件, 包含build/generated的子目录中生成的类和资源(会打包进jar文件中))
            // 输出的jar包路径 参考工程下的 Transform输出的jar文件和目录.txt
            input.jarInputs.forEach { jarInput ->
                // 注意: jarInput.file.name 和 jarInput.name 获取的名称不一样, 例如: jarInput.file.name获取的名称: classes.jar   jarInput.name获取的名称: :base
                val src = jarInput.file
                val srcFileName = if (jarInput.name.endsWith(".jar")) jarInput.name.substring(0, jarInput.name.length - 4) else jarInput.name
                // 重命名输出文件 (防止文件名称相同)
                val destFileName = "${srcFileName}_${DigestUtils.md5Hex(src.absolutePath)}"
                val dest = outputProvider.getContentLocation(destFileName, jarInput.contentTypes, jarInput.scopes, Format.JAR)

                mPrintUtil.print("源文件状态 ->> ${jarInput.status.name}")
                mPrintUtil.print("源文件名称 ->> ${jarInput.name}")
                mPrintUtil.print("源文件路径 ->> ${src.absolutePath}")
                mPrintUtil.print("目标文件名称 ->> $destFileName")
                mPrintUtil.print("目标文件路径 ->> ${dest.absolutePath}")

                // 扫描 jar 包
                val includeWeaveClass = mScanner.scanJar(src)

                if (includeWeaveClass
                    && src != null
                    && src.exists()
                    && includeWeaveClassDest == null
                ) {
                    // 需要等代码织入后再执行 FileUtils.copyFile(src, dest)
                    // 因为 当前插件的 input文件 和 output文件 都无法修改和删除 (有其他java进程占用)
                    includedWeaveClassSrc = src
                    includeWeaveClassDest = dest
                } else {
                    // 将 input 的 jar文件 复制到 output 指定 jar文件, 修改时使用, 用于下一个插件的输入
                    // 复制文件到当前执行的应用模块的指定目录(build/intermediates/transforms/this.getName函数返回的名称/debug或release/)
                    // 如需查看输出的jar文件, 请一定先看输出目录中的 __content__.json 文件(里面是jar文件的信息)
                    FileUtils.copyFile(src, dest)
                }
            }
            mPrintUtil.print("******************** jar包遍历完成 耗时:${System.currentTimeMillis() - startTime}毫秒  ********************")
            mPrintUtil.print()
            mPrintUtil.print("******************** 开始遍历目录 ********************")
            startTime = System.currentTimeMillis()
            // 遍历目录 (当前应用模块会生成目录, 包含build/generated的子目录中生成的类和资源)
            // 输出的目录路径 参考工程下的 Transform输出的jar文件和目录.txt
            input.directoryInputs.forEach { directoryInput ->
                val src = directoryInput.file
                val dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)

                mPrintUtil.print("目录名称: ${directoryInput.name}")
                mPrintUtil.print("源目录路径: ${src.absolutePath}")
                mPrintUtil.print("目标目录路径: ${dest.absolutePath}")

                var rootPath = src.absolutePath

                if (!rootPath.endsWith(File.separator)) {
                    rootPath += File.separator
                }
                src.recurseTraverse { file ->
                    val backHalfPath = file.absolutePath
                        .replaceFirst(rootPath, "")
                        .replaceToStandardFileSeparator()

                    if (file.isFile && mScanner.needScan(backHalfPath)) {
                        mScanner.scanClass(file.inputStream())
                    }
                }

                // 将 input 的 目录 复制到 output 指定 目录, 修改时使用, 用于下一个插件的输入
                // 复制目录到当前执行的应用模块的指定目录(build/intermediates/transforms/this.getName函数返回的名称/debug或release/)
                // 如需查看输出的目录, 请一定先看输出目录中的 __content__.json 文件(里面是输出目录的信息)
                FileUtils.copyDirectory(src, dest)
            }
            mPrintUtil.print("******************** 目录遍历完成 耗时:${System.currentTimeMillis() - startTime}毫秒 ********************")
        }
        mPrintUtil.print()
        mPrintUtil.print("******************** 开始织入代码 ********************")
        mPrintUtil.print("要织入的类名称列表: ${mClassNameSet.joinToString(", ")}")
        startTime = System.currentTimeMillis()
        if (includeWeaveClassDest != null) {
            val modifiedFile = mWeaver.weave(includedWeaveClassSrc!!)

            FileUtils.copyFile(modifiedFile, includeWeaveClassDest)
            // 删除修改后的临时文件
            modifiedFile.deleteOnExit()
        }
        mPrintUtil.print("******************** 织入完成 耗时:${System.currentTimeMillis() - startTime}毫秒 ********************")
        mPrintUtil.print("模块事件分发器插件耗时: ${System.currentTimeMillis() - allStartTime}毫秒")
    }

    override fun getName() = this::class.qualifiedName

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> = CONTENT_CLASS

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> = SCOPE_FULL_PROJECT

    override fun isIncremental() = false

}