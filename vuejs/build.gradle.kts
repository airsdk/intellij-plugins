// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
apply(from = "../contrib-configuration/common.gradle.kts")

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij")
}

val targetVersion = rootProject.extensions.get("targetVersion")

intellij {
  pluginName.set("Vue.js")

  plugins.set(listOf("JavaScriptLanguage", "JSIntentionPowerPack", "JavaScriptDebugger", "CSS", "HtmlTools",
         "org.jetbrains.plugins.sass", "org.jetbrains.plugins.less", "org.jetbrains.plugins.stylus",
         "org.intellij.plugins.postcss:$targetVersion",
         "com.jetbrains.plugins.Jade:$targetVersion",
         "intellij.prettierJS:$targetVersion",
         "intellij.webpack:$targetVersion"))

  version.set("LATEST-EAP-SNAPSHOT")
  type.set("IU")
}

sourceSets {
  main {
    java {
      setSrcDirs(listOf("src", "gen"))
    }
    resources {
      setSrcDirs(listOf("resources"))
    }
  }
  test {
    java {
      //setSrcDirs(listOf("vuejs-tests/src"))
    }
  }
}

dependencies {
  //testImplementation("com.jetbrains.intellij.javascript:javascript-test-framework:LATEST-EAP-SNAPSHOT")
}