package org.zenframework.z8.gradle

import org.eclipse.core.runtime.IPath
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.zenframework.z8.compiler.workspace.ProjectProperties

class Z8BasePlugin implements Plugin<Project> {

	static final String Z8_DEFAULT_VERSION = '1.3.0'

	@Override
	void apply(Project project) {
		if (!project.hasProperty('z8Version'))
			project.ext.z8Version = Z8_DEFAULT_VERSION
		if (!project.hasProperty('srcMainDir'))
			project.ext.srcMainDir = project.file("${project.projectDir}/src/main")
		if (!project.hasProperty('resolveGroups'))
			project.ext.resolveGroups = [ project.group ]

		project.configurations.all {
			resolutionStrategy.dependencySubstitution.all { DependencySubstitution dependency ->
				if (dependency.requested instanceof ModuleComponentSelector) {
					def resolve = project.resolveGroups.find { dependency.requested.group.startsWith it } != null
					project.logger.debug "Resolve ${dependency.requested.group} by ${project.resolveGroups} ... ${resolve ? 'Ok' : 'Skip'}"
					if (resolve) {
						def targetProject = project.findProject(":${dependency.requested.module}")
						if (targetProject != null) {
							project.logger.info "Z8 [${project.name}]: substitute ${dependency.requested.displayName} by ${targetProject}"
							dependency.useTarget targetProject
						}
					}
				}
			}
		}

		project.tasks.register('z8Info', DefaultTask) {
			doLast {
				println "Z8 Project [${project.name}] sources main dir: ${project.srcMainDir}"
			}
		}

		project.tasks.register('z8zip', Zip) {
			group 'Build'
			description 'Assemble BL + JS/CSS archive'

			archiveName "${project.name}-${project.version}.zip"
			destinationDir project.file("${project.buildDir}/libs/")

			from("${project.buildDir}/bl") {
				include '**/*'
				includeEmptyDirs = false
			}

			from(project.buildDir) {
				include 'web/**/*'
				includeEmptyDirs = false
			}

			from(project.buildDir) {
				include 'conf/**/*'
				includeEmptyDirs = false
			}
		}

		project.tasks.register('collectConf', Copy) {
			group 'Build'
			description 'Copy conf files'
			from"${project.srcMainDir}/conf"
			into "${project.buildDir}/conf"
		}
		project.tasks.z8zip.dependsOn project.tasks.collectConf

		project.pluginManager.withPlugin('maven-publish') {
			project.publishing {
				repositories { mavenLocal() }
				publications {
					mavenZ8(MavenPublication) { artifact source: project.tasks.z8zip, extension: 'zip' }
				}
			}
		}
	}

}
