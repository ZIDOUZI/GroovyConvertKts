package zdz.groovyconvertkts.core

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.text.RegexOption.DOT_MATCHES_ALL

fun getClipboardContents(): String {

    print("[${currentTimeFormatted()}] - Trying to open clipboard.. ")

    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val contents = clipboard.getContents(null)
    val hasTransferableText = contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)

    val result =
        if (hasTransferableText) contents?.getTransferData(DataFlavor.stringFlavor) as? String ?: "" else ""

    println("Success!")
    return result
}

fun writeToClipboard(text: String) {
    val selection = StringSelection(text)
    val clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()

    print("[${currentTimeFormatted()}] --- Saving to clipboard.. ")

    clipboard.setContents(selection, selection)
}

fun currentTimeFormatted(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

fun processFile(file: File) {
    print("[${currentTimeFormatted()}] - Trying to open file.. ")
    if (!file.exists()) {
        println("Didn't find a file in the path you specified. Exiting...")
    }
    println("Success!")
    ////////读写分界线
    val fileIsAlreadyKts = file.path.takeLast(4) == ".kts"

    if (fileIsAlreadyKts) {
        println(
            "\n### ### ### Warning! The script will overrite ${file.path}, since it ends with \".kts\"".red() +
                    "\n### ### ### Gradle might get crazy and all red, so you might want to \"gradle build\"\n".red()
        )
    }

    val newFilePath = if (fileIsAlreadyKts) file.path else "${file.path}.kts"

    println("[${currentTimeFormatted()}] --- Saving to: \"$newFilePath\".. ")

    val newFile = File(newFilePath)
    newFile.createNewFile()
    newFile.writeText(convert(file.readText()))
}

// from https://github.com/importre/crayon
fun String.bold() = "\u001b[1m${this}\u001b[0m"
fun String.cyan() = "\u001b[36m${this}\u001b[0m"
fun String.green() = "\u001b[32m${this}\u001b[0m"
fun String.magenta() = "\u001b[35m${this}\u001b[0m"
fun String.red() = "\u001b[31m${this}\u001b[0m"
fun String.yellow() = "\u001b[33m${this}\u001b[0m"

fun convert(text: String): String {

    fun String.replaceApostrophes(): String = this.replace("'", "\"")

    fun String.replaceDefWithVal(): String = this.replace("(^|\\s)def ".toRegex()) { valReplacer ->
        valReplacer.value.replace("def", "val")
    }

    fun String.convertType(): String =
        when (this) {
            "byte" -> "Byte"
            "short" -> "Short"
            "int" -> "Int"
            "long" -> "Long"
            "float" -> "Float"
            "double" -> "Double"
            "char" -> "Char"
            "boolean" -> "Boolean"
            else -> this
        }

    fun String.convertVariableDeclaration(): String {
        val varDeclExp = """(?:final\s+)?(\w+)(<.+>)? +(\w+)\s*=\s*(.+)""".toRegex()

        return this.replace(varDeclExp) {
            val (type, genericsType, id, value) = it.destructured
            if (type == "val") {
                it.value
            } else {
                "val $id: ${type.convertType()}${genericsType} = $value"
            }
        }
    }

    fun String.convertMapExpression(): String {
        val key = """\w+"""
        val value = """[^,:\s\]]+"""
        val keyValueGroup = """\s*$key:\s*$value\s*"""
        val mapRegExp = Regex("""\[($keyValueGroup(?:,$keyValueGroup)*)]""", DOT_MATCHES_ALL)
        val extractOneGroupRegExp =
            Regex("""^\s*($key):\s*($value)\s*(?:,(.*)|)$""") // Matches key, value, the-rest after comma if any

        fun extractAllMatches(
            matchesInKotlinCode: MutableList<String>,
            remainingString: String
        ) { // Extract the first key=value, and recurse on the postfix
            val innerMatch: MatchResult = extractOneGroupRegExp.find(remainingString) ?: return
            val innerGroups = innerMatch.groupValues
            matchesInKotlinCode += """"${innerGroups[1]}" to ${innerGroups[2]}"""
            if (innerGroups[3].isNotEmpty()) {
                val withoutComma = innerGroups[3]//.substring(1)
                extractAllMatches(matchesInKotlinCode, withoutComma)
            }
        }

        return this.replace(mapRegExp) { lineMatch ->
            val matchesInKotlinCode = mutableListOf<String>()
            extractAllMatches(matchesInKotlinCode, lineMatch.groupValues[1])
            "mapOf(${matchesInKotlinCode.joinToString(", ")})"
        }
    }

    fun String.convertManifestPlaceHoldersWithMap(): String {
        val regExp = """manifestPlaceholders = (mapOf\([^)]*\))""".toRegex(DOT_MATCHES_ALL)
        return this.replace(regExp) {
            "manifestPlaceholders.putAll(${it.groupValues[1]})"
        }
    }

    fun String.convertArrayExpression(): String {
        val arrayExp = """\[([^]]*?)]""".toRegex(DOT_MATCHES_ALL)

        return this.replace(arrayExp) {
            if (it.groupValues[1].toIntOrNull() != null) {
                it.value // Its probably an array indexing, so keep original
            } else {
                "listOf(${it.groupValues[1]})"
            }
        }
    }

    fun String.convertVariantFilter(): String {
        val arrayExp = """variantFilter\s*\{\s*(\w+\s*->)""".toRegex(DOT_MATCHES_ALL)

        return this.replace(arrayExp) {
            "variantFilter { // ${it.groupValues[1]} - TODO Manually replace '${it.groupValues[1]}' variable with this, and setIgnore(true) with ignore = true\n"
        }
    }

    fun String.convertPlugins(): String {
        val pluginsExp = """apply plugin: (\S+)""".toRegex()

        return this.replace(pluginsExp) {
            val (pluginId) = it.destructured
            // it identifies the plugin id and rebuilds the line.
            "apply(plugin = $pluginId)"
        }
    }

    fun String.getExpressionBlock(
        expression: Regex,
        modifyResult: ((String) -> (String))
    ): String {

        val stringSize = this.count()

        return expression.findAll(this)
            .toList()
            .foldRight(this) { matchResult, accString ->

                val rangeStart = matchResult.range.last
                var rangeEnd = stringSize
                var count = 0

                println("[DP] - range: ${matchResult.range} value: ${matchResult.value}")

                for (item in rangeStart..stringSize) {
                    if (this[item] == '{') count += 1 else if (this[item] == '}') count -= 1
                    if (count == 0) {
                        rangeEnd = item
                        break
                    }
                }

                println("[DP] reading this block:\n${this.substring(rangeStart, rangeEnd)}")

                val convertedStr = modifyResult.invoke(this.substring(rangeStart, rangeEnd))

                println("[DP] outputing this block:\n${convertedStr}")

                accString.replaceRange(rangeStart, rangeEnd, convertedStr)
            }
    }

    fun String.convertNestedTypes(buildTypes: String, named: String): String {
        return this.getExpressionBlock("$buildTypes\\s*\\{".toRegex()) { substring ->
            substring.replace("\\S*\\s(?=\\{)".toRegex()) {
                val valueWithoutWhitespace = it.value.replace(" ", "")
                "$named(\"$valueWithoutWhitespace\") "
            }
        }
    }

    fun String.addIsToStr(blockTitle: String, transform: String): String {

        val extensionsExp = "$blockTitle\\s*\\{[\\s\\S]*}".toRegex()

        if (!extensionsExp.containsMatchIn(this)) return this

        val typesExp = "$transform.*".toRegex()

        return this.replace(typesExp) { matchResult ->

            val split = matchResult.value.split(" ")

            println("[AS] split:\n${split}")


            // if there is more than one whitespace, the last().toIntOrNull() will find.
            if (split.lastOrNull { it.isNotBlank() } != null) {
                "is${split[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} = ${split.last()}"
            } else {
                matchResult.value
            }
        }
    }

    fun String.convertPluginsFrom(): String {
        val pluginsExp = """apply from: (\S+)""".toRegex()

        return this.replace(pluginsExp) {
            val (pluginId) = it.destructured
            "apply(from = $pluginId)"
        }
    }

    fun String.convertAndroidBuildConfigFunctions(): String {
        val outerExp = """(buildConfigField|resValue|flavorDimensions|exclude|java.srcDir)\s+(".*")""".toRegex()
        // packagingOptions > exclude
        // sourceSets > name("") > java.srcDir

        return this.replace(outerExp) {
            val groups = it.groupValues
            "${groups[1]}(${groups[2]})"
        }
    }

    fun String.convertCompileToImplementation(): String {
        val outerExp = "(compile|testCompile)(?!O).*\".*\"".toRegex()

        return this.replace(outerExp) {
            if ("testCompile" in it.value) {
                it.value.replace("testCompile", "testImplementation")
            } else {
                it.value.replace("compile", "implementation")
            }
        }
    }

    fun String.convertDependencies(): String {

        val testKeywords =
            "testImplementation|androidTestImplementation|debugImplementation|compileOnly|testCompileOnly|runtimeOnly|developmentOnly"
        val gradleKeywords =
            "($testKeywords|implementation|api|annotationProcessor|classpath|kaptTest|kaptAndroidTest|kapt|check)".toRegex()

        // ignore cases like kapt { correctErrorTypes = true } and apply plugin: ('kotlin-kapt") but pass kapt("...")
        // ignore keyWord followed by a space and a { or a " and a )
        val validKeywords = "(?!$gradleKeywords\\s*(\\{|\"\\)|\\.))$gradleKeywords.*".toRegex()

        return this.replace(validKeywords) { substring ->
            // By pass sth like: implementation(":epoxy-annotations") { ... }
            if (substring.value.contains("""\)(\s*)\{""".toRegex())) return@replace substring.value

            // retrieve the comment [//this is a comment], if any
            val comment = "\\s*//.*".toRegex().find(substring.value)?.value ?: ""

            // remove the comment from the string. It will be added again at the end.
            val processedSubstring = substring.value.replace(comment, "")

            // we want to know if it is a implementation, api, etc
            val gradleKeyword = gradleKeywords.find(processedSubstring)?.value

            // implementation ':epoxy-annotations' becomes 'epoxy-annotations'
            val isolated = processedSubstring.replaceFirst(gradleKeywords, "").trim()

            // can't be && for the kapt project(':epoxy-processor') scenario, where there is a ) on the last element.
            if (isolated != "" && (isolated.first() != '(' || isolated.last { it != ' ' } != ')')) {
                "$gradleKeyword($isolated)$comment"
            } else {
                "$gradleKeyword$isolated$comment"
            }
        }
    }

    fun String.convertFileTree(): String {
        val fileTreeString = """fileTree\(dir(\s*):(\s*)"libs"(\s*),(\s*)include(\s*):(\s*)\["\*.jar"]\)""".toRegex()

        return this.replace(fileTreeString, """fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))""")
    }

    fun String.convertSigningConfigBuildType(): String {
        val outerExp = "signingConfig.*signingConfigs.*".toRegex()

        return this.replace(outerExp) {
            // extracts release from signingConfig signingConfigs.release
            val release = it.value.replace("signingConfig.*signingConfigs.".toRegex(), "")
            "signingConfig = signingConfigs.getByName(\"$release\")"
        }
    }

    fun String.convertBuildTypes(): String = this.convertNestedTypes("buildTypes", "named")

    fun String.convertProductFlavors(): String = this.convertNestedTypes("productFlavors", "create")

    fun String.convertSourceSets(): String = this.convertNestedTypes("sourceSets", "named")

    fun String.convertSigningConfigs(): String = this.convertNestedTypes("signingConfigs", "register")

    fun String.convertMaven(): String {

        val mavenExp = "maven\\s*\\{\\s*url\\s*(.*?)\\s*?}".toRegex()

        return this.replace(mavenExp) {
            it.value.replace("(= *uri *\\()|\\)|(url)|( )".toRegex(), "")
                .replace("{", "(")
                .replace("}", ")")
        }
    }

    var showWarningGroovyVariables = false

    fun String.addParentheses(): String {

        val sdkExp =
            "(compileSdkVersion|minSdkVersion|targetSdkVersion)\\s*([^\\s]*)(.*)".toRegex() // include any word, as it may be a variable

        return this.replace(sdkExp) {
            val groups = it.groupValues
            if (groups.size > 3) {
                if (groups[2].toIntOrNull() == null) showWarningGroovyVariables = true
                "${groups[1]}(${groups[2]})${groups[3]}" // group 3 for preserving comments
            } else {
                it.value
            }
        }
    }

    fun String.addParenthesisToId(): String {

        // this will only catch id "..." version ..., should skip id("...")
        // should get the id "..."
        val idExp = "id\\s+\".*?\"".toRegex()

        return this.replace(idExp) {
            // remove the "id " before the real id
            val idValue = it.value.replace("id\\s+".toRegex(), "")
            "id($idValue)"
        }
    }

    fun String.addEquals(): String {

        val signing = "keyAlias|keyPassword|storeFile|storePassword"
        val other = "multiDexEnabled|correctErrorTypes|javaMaxHeapSize|jumboMode|dimension|useSupportLibrary"
        val dataBinding = "dataBinding|viewBinding"
        val defaultConfig =
            "applicationId|versionCode|versionName|testInstrumentationRunner|namespace|compileSdk|minSdk|targetSdk"
        val compose = "compose|kotlinCompilerExtensionVersion"
        val negativeLookAhead = "(?!\\{)[^\\s]" // Don't want '{' as next word character

        val versionExp = """($defaultConfig|$signing|$other|$dataBinding|$compose)\s+${negativeLookAhead}.*""".toRegex()

        return this.replace(versionExp) {
            val split = it.value.split(" ")

            // if there is more than one whitespace, the last().toIntOrNull() will find.
            if (split.lastOrNull { s -> s.isNotBlank() } != null) {
                "${split[0]} = ${split.last()}"
            } else {
                it.value
            }
        }
    }

    fun String.convertProguardFiles(): String {

        val proguardExp = "proguardFiles .*".toRegex()

        return this.replace(proguardExp) {
            val isolatedArgs = it.value.replace("proguardFiles\\s*".toRegex(), "")
            "setProguardFiles(listOf($isolatedArgs))"
        }
    }

    fun String.convertExtToExtra(): String {

        // get ext... but not ext { ... }
        val outerExp = """ext\.(\w+)\s*=\s*(.*)""".toRegex()

        return this.replace(outerExp) {
            val (name, value) = it.destructured

            "extra[\"$name\"] = $value"
        }
    }

    fun String.convertJavaCompatibility(): String {

        val compatibilityExp = "(sourceCompatibility|targetCompatibility).*".toRegex()

        return this.replace(compatibilityExp) {
            val split = it.value.replace("\"]*".toRegex(), "").split(" ")

            if (split.lastOrNull() != null) {
                if ("JavaVersion" in split.last()) {
                    "${split[0]} = ${split.last()}"
                } else {
                    "${split[0]} = JavaVersion.VERSION_${split.last().replace(".", "_")}"
                }
            } else {
                it.value
            }
        }
    }

    fun String.convertCleanTask(): String {

        val cleanExp = "task clean\\(type: Delete\\)\\s*\\{[\\s\\S]*}".toRegex()
        val registerClean = "tasks.register<Delete>(\"clean\").configure {\n" +
                "    delete(rootProject.buildDir)\n }"

        return this.replace(cleanExp, registerClean)
    }

    fun String.convertInternalBlocks(): String {
        return this.addIsToStr("androidExtensions", "experimental")
            .addIsToStr("dataBinding", "enabled")
            .addIsToStr("lintOptions", "abortOnError")
            .addIsToStr("buildTypes", "debuggable")
            .addIsToStr("buildTypes", "minifyEnabled")
            .addIsToStr("buildTypes", "shrinkResources")
            .addIsToStr("", "transitive")
    }

    fun String.convertInclude(): String {

        val expressionBase = "\\s*((\".*\"\\s*,)\\s*)*(\".*\")".toRegex()
        val includeExp = "include$expressionBase".toRegex()

        return this.replace(includeExp) { includeBlock ->
            if (includeBlock.value.contains("include\"")) return@replace includeBlock.value // exclude: "include" to

            // avoid cases where some lines at the start/end are blank
            val multiLine = includeBlock.value.split('\n').count { it.isNotBlank() } > 1

            val isolated = expressionBase.find(includeBlock.value)?.value ?: ""
            if (multiLine) "include(\n${isolated.trim()}\n)" else "include(${isolated.trim()})"
            // Possible visual improvement: when using multiline, the first line should have the same
            // margin/spacement as the others.
        }
    }

    fun String.convertExcludeClasspath(): String {

        val fullLineExp = ".*configurations\\.classpath\\.exclude.*group:.*".toRegex()

        println("[CEC] - reading this line: " + fullLineExp.find(this)?.value)


        // this will extract "com.android.tools.external.lombok" from the string.
        val innerExp = "\".*\"".toRegex()

        return this.replace(fullLineExp) { isolatedLine ->
            val isolatedStr = innerExp.find(isolatedLine.value)?.value ?: ""
            "configurations.classpath {\n" +
                    "    exclude(group = $isolatedStr)\n" +
                    "}"
        }
    }

    fun String.convertExcludeModules(): String {
        val fullLineExp = """exclude module: (\S+)""".toRegex()

        return this.replace(fullLineExp) {
            val (moduleId) = it.destructured
            "exclude(module = $moduleId)"
        }
    }

    fun String.convertExcludeGroups(): String {
        val fullLineExp = """exclude group: (\S+)""".toRegex()

        return this.replace(fullLineExp) {
            val (groupId) = it.destructured
            "exclude(group = $groupId)"
        }
    }

    fun String.convertJetBrainsKotlin(): String {

        // if string is implementation("..."), this will extract only the ...
        val fullLineExp = "\"org.jetbrains.kotlin:kotlin-.*(?=\\))".toRegex()

        val removeExp = "(?!org.jetbrains.kotlin:kotlin)-.*".toRegex()

        var shouldImportKotlinCompiler = false

        val newText = this.replace(fullLineExp) { isolatedLine ->

            // drop first "-" and remove last "
            val substring = (removeExp.find(isolatedLine.value)?.value ?: "").drop(1).replace("\"", "")

            val splittedSubstring = substring.split(":")

            if ("stdlib" in substring) {
                shouldImportKotlinCompiler = true
                "kotlin(\"stdlib\", KotlinCompilerVersion.VERSION)"
            } else if (splittedSubstring.size == 2) {
                "kotlin(\"${splittedSubstring[0]}\", version = \"${splittedSubstring[1]}\")"
            } else {
                "kotlin(\"${splittedSubstring[0]}\")"
            }
        }

        return if (shouldImportKotlinCompiler) {
            "import org.jetbrains.kotlin.config.KotlinCompilerVersion\n\n" + newText
        } else {
            newText
        }
    }

    fun String.convertPluginsIntoOneBlock(): String {

        // group plugin expressions. There can't be any space or tabs on the start of the line, else the regex will fail.
        // ok example:
        // apply(...)
        // apply(...)
        //
        // not ok example:
        // apply(...)
        //    apply(...)
        val fullLineExp = "(apply\\(plugin\\s*=\\s*\".*\"\\)[\\s\\S]){2,}".toRegex()

        val isolatedId = "\".*\"(?=\\))".toRegex()

        return this.replace(fullLineExp) { isolatedLine ->
            // this will fold the ids into a single string
            val plugins = isolatedId.findAll(isolatedLine.value).fold("") { acc, matchResult ->
                acc + "    id(${matchResult.value})\n"
            }
            "plugins {\n$plugins}\n"
        }
    }

    fun String.replaceColonWithEquals(): String {

        // this get "group:"
        val expression = "\\w*:\\s*\".*?\"".toRegex()

        return this.replace(expression) {
            it.value.replace(":", " =")
        }
    }

    val convertedText = text
        .replaceApostrophes()
        .replaceDefWithVal()
        .convertMapExpression() // Run before array
        .convertFileTree()
        .convertArrayExpression()
        .convertManifestPlaceHoldersWithMap() // Run after convertMapExpression
        .convertVariableDeclaration()
        .convertPlugins()
        .convertPluginsIntoOneBlock()
        .convertPluginsFrom()
        .convertVariantFilter()
        .convertAndroidBuildConfigFunctions()
        .convertCompileToImplementation()
        .convertDependencies()
        .convertMaven()
        .addParentheses()
        .addEquals()
        .convertJavaCompatibility()
        .convertCleanTask()
        .convertProguardFiles()
        .convertInternalBlocks()
        .convertInclude()
        .convertBuildTypes()
        .convertProductFlavors()
        .convertSourceSets()
        .convertSigningConfigs()
        .convertExcludeClasspath()
        .convertExcludeModules()
        .convertExcludeGroups()
        .convertJetBrainsKotlin()
        .convertSigningConfigBuildType()
        .convertExtToExtra()
        .addParenthesisToId()
        .replaceColonWithEquals()

    println("[${currentTimeFormatted()}] -- Starting conversion.. ")
    println("Success!")
    if (showWarningGroovyVariables) {
        println("\nWarning: We detected non-integer values for compileSdkVersion | minSdkVersion | targetSdkVersion\n- Groovy ext-variables are not supported, see buildSrc instead: https://proandroiddev.com/converting-your-android-gradle-scripts-to-kotlin-1172f1069880")
    }

    return convertedText
}

fun changeToLibrary(text: String): String {

    fun String.deleteApplicationID(): String =
        this.replace(Regex("applicationId = \"([-A-Za-z0-9_]+)\"\n"), "")

    fun String.changeToLib(): String = this.replace("id(\"com.android.application\")", "id(\"com.android.library\")")

    fun String.deleteVersionName(): String = this.replace(Regex("versionCode = .+\n"), "")

    fun String.deleteVersionCode(): String = this.replace(Regex("versionCode = \\d+\n"), "")

    return text.deleteApplicationID()
        .changeToLib()
        .deleteVersionName()
        .deleteVersionCode()

}

//TODO: 项目名含有compose时会加等号