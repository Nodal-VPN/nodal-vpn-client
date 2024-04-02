pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.0' 
		jdk 'Graal JDK 17' 
	}

	stages {
		stage ('LogonBox VPN Client Installers') {
			parallel {
				/*
				 * Linux Installers and Packages
				 */
				stage ('Linux LogonBox VPN Client Installers') {
					agent {
						label 'install4j && linux'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
					 		) {
					 		  	sh 'mvn -U -Dbuild.mediaTypes=unixInstaller,unixArchive,linuxRPM,linuxDeb -Dbuild.projectProperties=$BUILD_PROPERTIES -P build-install4j-logonbox-vpn-client -pl client-logonbox-vpn-installer clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'client-logonbox-vpn-installer/target/media/*', name: 'linux-vpn-client'
			        			
			        			/* Stash updates.xml */
			        			dir('client-logonbox-vpn-installer/target/media') {
									stash includes: 'updates.xml', name: 'linux-updates-xml'
			        			}
					 		}
        				}
					}
				}
				
				/*
				 * MacOS installers
				 */
				stage ('MacOS LogonBox VPN Client Installers') {
					agent {
						label 'install4j && macos'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
					 		) {
					 			// -Dinstall4j.disableNotarization=true 
					 		  	sh 'mvn -U -Dinstall4j.verbose=true -Dbuild.mediaTypes=macos,macosFolder,macosFolderArchive -Dbuild.projectProperties=$BUILD_PROPERTIES -P build-install4j-logonbox-vpn-client -pl client-logonbox-vpn-installer clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'client-logonbox-vpn-installer/target/media/*', name: 'macos-vpn-client'
			        			
			        			/* Stash updates.xml */
			        			dir('client-logonbox-vpn-installer/target/media') {
									stash includes: 'updates.xml', name: 'macos-updates-xml'
			        			}
					 		}
        				}
					}
				}
				
				/*
				 * Windows installers
				 */
				stage ('Windows LogonBox VPN Client Installers') {
					agent {
						label 'install4j && windows'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
					 		) {
					 		  	bat 'mvn -U -Dbuild.mediaTypes=windows,windowsArchive "-Dbuild.projectProperties=%BUILD_PROPERTIES%" -P build-install4j-logonbox-vpn-client -pl client-logonbox-vpn-installer clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'client-logonbox-vpn-installer/target/media/*', name: 'windows-vpn-client'
			        			
			        			/* Stash updates.xml */
			        			dir('client-logonbox-vpn-installer/target/media') {
									stash includes: 'updates.xml', name: 'windows-updates-xml'
			        			}
					 		}
        				}
					}
				}
			}
		}
		stage ('Deploy') {
			agent {
				label 'linux'
			}
			steps {
			
				script {
					/* Create full version number from Maven POM version and the
					   build number */
					def pom = readMavenPom file: 'pom.xml'
					pom_version_array = pom.version.split('\\.')
					suffix_array = pom_version_array[2].split('-')
					env.FULL_VERSION = pom_version_array[0] + '.' + pom_version_array[1] + "." + suffix_array[0] + "-${BUILD_NUMBER}"
					echo 'Full Maven Version ' + env.FULL_VERSION
				}
				
				/* Unstash installers */
	 		  	unstash 'windows-vpn-client'
	 		  	unstash 'linux-vpn-client'
	 		  	unstash 'macos-vpn-client'
	 		  	
				/* Unstash updates.xml */
	 		  	dir('client-logonbox-vpn-installer/target/media-macos') {
	 		  		unstash 'macos-updates-xml'
    			}
	 		  	dir('client-logonbox-vpn-installer/target/media-windows') {
	 		  		unstash 'windows-updates-xml'
    			}
	 		  	dir('client-logonbox-vpn-installer/target/media-linux') {
	 		  		unstash 'linux-updates-xml'
    			}
    			
    			/* Merge all updates.xml into one */
    			withMaven(
		 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde',
		 		) {
					sh 'mvn -P merge-installers -pl client-logonbox-vpn-installer com.sshtools:updatesxmlmerger-maven-plugin:merge'
		 		}
		 		
    			/* Upload all installers and updates.xml for this build number */
		 		s3Upload(
		 			consoleLogLevel: 'INFO', 
		 			dontSetBuildResultOnFailure: false, 
		 			dontWaitForConcurrentBuildCompletion: false, 
		 			entries: [[
		 				bucket: 'logonbox-packages/logonbox-vpn-client/' + env.FULL_VERSION, 
		 				noUploadOnFailure: true, 
		 				selectedRegion: 'eu-west-1', 
		 				sourceFile: 'client-logonbox-vpn-installer/target/media/*', 
		 				storageClass: 'STANDARD', 
		 				useServerSideEncryption: false]], 
		 			pluginFailureResultConstraint: 'FAILURE', 
		 			profileName: 'LogonBox Buckets', 
		 			userMetadata: []
		 		)
		 		
    			/* Copy the merged updates.xml to the nightly directory so updates can be seen
    			by anyone on this channel */
		 		s3Upload(
		 			consoleLogLevel: 'INFO', 
		 			dontSetBuildResultOnFailure: false, 
		 			dontWaitForConcurrentBuildCompletion: false, 
		 			entries: [[
		 				bucket: 'logonbox-packages/logonbox-vpn-client/nightly', 
		 				noUploadOnFailure: true, 
		 				selectedRegion: 'eu-west-1', 
		 				sourceFile: 'client-logonbox-vpn-installer/target/media/updates.xml', 
		 				storageClass: 'STANDARD', 
		 				useServerSideEncryption: false]], 
		 			pluginFailureResultConstraint: 'FAILURE', 
		 			profileName: 'LogonBox Buckets', 
		 			userMetadata: []
		 		)
			}					
		}		
	}
}