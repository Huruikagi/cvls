import picocli.CommandLine
import picocli.CommandLine.*
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import kotlin.streams.toList
import kotlin.system.exitProcess

@Command(
    name = "cvls",
    version = ["1.0.0"],
    description = ["Convert Line Separator tool."],
    mixinStandardHelpOptions = true
)
class Cvls: Callable<Int> {

    @Parameters(
        arity = "1..*",
        description = ["ターゲットとするファイルやディレクトリ"]
    )
    private lateinit var targetPaths: Array<Path>

    @Option(
        names = ["-r"],
        description = [""]
    )
    private var recursive: Boolean = false

    private val tempDir: Path = Paths.get(System.getProperty("java.io.tmpdir"))
        .resolve("cvls")

    override fun call(): Int {
        // picocliとしてのエントリポイント。

        var exitCode = 0

        // 作業ディレクトリを初期化
        Files.deleteIfExists(tempDir)
        Files.createDirectory(tempDir)

        try {
            // メイン処理実行
            processPassedPaths()
        } catch (err: CvlsException) {
            // エラー時
            System.err.println(err.message)
            exitCode = -1
        }

        // 作業ディレクトリを削除しておく
        Files.delete(tempDir)

        return exitCode
    }

    private fun processPassedPaths() {
        // コマンドライン引数で指定された各パスを処理していく。
        targetPaths.forEachIndexed { i, path ->
            if (Files.notExists(path)) {
                // 直接指定されたパスに何も存在しなかった場合はエラー
                throw CvlsException("err: $path is not exists.")
            } else {
                // ディレクトリかファイルかに応じた処理を行う
                val ttmp = tempDir.resolve(i.toString())
                Files.createDirectory(ttmp)
                path.toFile().copyRecursively(ttmp.toFile())
                if (Files.isDirectory(path)) {
                    convertDirectory(path)
                } else {
                    convertFile(path)
                }
            }
        }
    }

    private fun convertDirectory(dir: Path) {

        Files.list(dir) // 子要素のStreamを取得
            .toList()   // Streamのままだとエラー処理とか面倒なのでKotlinのListにしておく
            .forEach {
                if (Files.isDirectory(it)) {
                    // ディレクトリは再起指定されていた場合のみ処理。指定されていなければ無視
                    if (recursive) {
                        convertDirectory(it)
                    }
                } else {
                    // ファイルを処理する
                    convertFile(it)
                }
            }
    }

    private fun convertFile(source: Path) {
        if (Files.notExists(source)) {
            // ここでなかったら怒る
            throw CvlsException("err: $source is not exists.")
        }
        val sourceFile = source.toFile()

        // 置き換えて書き込み
        sourceFile.writeText(
            convert(sourceFile.readText())
        )
    }

    private fun convert(source: String): String {
        return source
            .replace("\r\n", "\r")
            .replace("\r", "\r\n")
    }

}

/**
 * 想定内エラー用Exception
 */
class CvlsException(message: String): RuntimeException(message)

/**
 * JVMエントリポイント
 */
fun main(args: Array<String>) {
    exitProcess(CommandLine(Cvls()).execute(*args))
}
