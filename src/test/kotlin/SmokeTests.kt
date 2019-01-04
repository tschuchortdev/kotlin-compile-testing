import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.PrintStream
import javax.annotation.processing.Processor

class SmokeTests {
	@Rule @JvmField val temporaryFolder = TemporaryFolder()

	@Test
	fun `compilation succeeds`() {
		val kSource = KotlinCompilation.SourceFile(
			"kSource.kt",
			"""//import Marker
					import javax.lang.model.SourceVersion
					import java.io.File
					//import com.tschuchort.compiletest.KotlinGeneratedKotlinClass
					//import com.tschuchort.compiletest.JavaGeneratedKotlinClass

					class KotClass {
						fun brr() {}
					}

					@Marker
					fun foo() {
						JSource().bar()
					}

					fun main(freeArgs: Array<String>) {
						File("")
						KotlinGeneratedKotlinClass().foo()
						KotlinGeneratedKotlinClass().bar()
						JavaGeneratedKotlinClass().foo()
						JavaGeneratedKotlinClass().bar()
						KotlinGeneratedJavaClass()
						JavaGeneratedJavaClass()
					}
				""".trimIndent()
		)

		val jSource = KotlinCompilation.SourceFile(
			"JSource.java",
			"""
                //import com.tschuchort.compiletest.KotlinGeneratedKotlinClass;
                //import com.tschuchort.compiletest.JavaGeneratedKotlinClass;

    			public class JSource {
                    public JSource() {
                    	(new KotlinGeneratedKotlinClass()).bar();
                        (new KotlinGeneratedKotlinClass()).foo();
						(new JavaGeneratedKotlinClass()).foo();
						(new JavaGeneratedKotlinClass()).bar();
						KotlinGeneratedJavaClass c = new KotlinGeneratedJavaClass();
						JavaGeneratedJavaClass c2 = new JavaGeneratedJavaClass();
                    }

    				@Marker public void bar() {
    					(new KotClass()).brr();
    				}
                }
				""".trimIndent()
		)

		val systemOutBuffer = Buffer()

		val result = KotlinCompilation(
			workingDir = temporaryFolder.root,
			sources = listOf(kSource, jSource),
			services = listOf(
				KotlinCompilation.Service(Processor::class, KotlinTestProcessor::class),
				KotlinCompilation.Service(Processor::class, JavaTestProcessor::class)
			),
			jdkHome = File("D:\\Program Files\\Java\\jdk1.8.0_25"),//getJavaHome(),
			//toolsJar = File("D:\\Program Files\\Java\\jdk1.8.0_25\\lib\\tools.jar"),
			inheritClassPath = true,
			skipRuntimeVersionCheck = true,
			correctErrorTypes = true,
			verbose = true,
			//reportOutputFiles = true,
			systemOut = PrintStream(systemOutBuffer.outputStream())
		).run()

		print(systemOutBuffer.readUtf8())

		print(result.generatedFiles)

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}


}