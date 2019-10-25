package org.royole

import org.gradle.api.Project

class QualityExtension {

    File checkstyleSuppression

    QualityExtension(Project project) {
        //若目标工程未设置，则使用默认值
        checkstyleSuppression = new File(project.rootProject.buildDir, "quality/config/checkstyle/suppressions.xml")
    }

}
