# Kotlin Multiplatform XCFrameworks deployment plugin

KMP Apple binaries deployment Gradle plugin helps you publish your Kotlin Multiplatform project as an XCFramework end-to-end for Swift Package Manager and CocoaPods integrations.

The plugin is WIP. Please let me know in the Issues if something doesn't work or there is no integration for your case.

Currently supported are:
* [Git deployment as a Swift Package with XCFramework deployment to](#spm):  
  * [Repository with the Swift Package (easiest, you just need a git repo)](#spm-single-repo)
  * [File storage over http such as Artifactory Generic or Nexus Raw](#spm-http-xcframework)
  * [GitHub Packages](#spm-gh-packages-xcframework)
  * [GitHub Releases](#spm-gh-releases-xcframework)
* [CocoaPods Podspec with XCFramework deployment to](#cocoapods):
  * [Repository with the podspec (easiest, you just need a git repo)](#cocoapods-single-repo)
  * [Spec repository](#cocoapods-specs-repo)
  * [File storage over http for both the podspec and the XCFramework](#cocoapods-http)

The plugin is published to Maven Central, so you have to add it to plugin repositories:

```kotlin
// settings.gradle.kts

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}
```

and apply it in your build script:
```kotlin
// shared/build.gradle.kts

plugins { 
    kotlin("multiplatform")
    id("store.kmpd.plugin") version "0.0.2"
}
```

For commands that work with git repositories don't forget to add your private key via `ssh-add /path/to/id_rsa` or use `GIT_SSH_COMMAND`:
```shell
GIT_SSH_COMMAND='ssh -i /path/to/id_rsa -o IdentitiesOnly=yes' ./gradlew ...
```

When resolving XCFramework from an authenticated storage, don't forget to configure [netrc or Keychain](#xcframework_download_auth).

## <a name="spm"></a> Swift Package Manager

All currently supported Swift Package deployment options require a git repository where the `Package.swift` file will be created and pushed with an appropriate tag. It is better to dedicate a separate repository for the purpose of package deployment. 

Before specifying a deployment option, add the [XCFramework](https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks) to your build config:
```kotlin
// shared/build.gradle.kts

import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { 
    kotlin("multiplatform")
    id("store.kmpd.plugin")
}

kotlin { 
    val xcf = XCFramework()
    listOf(
        iosArm64(),
        iosX64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            xcf.add(this)
        }
    }

    appleBinariesDeployment {
        // Deployments configured here will use the XCFramework declared above
    }
}
```

Configured deployments create a task named `deploy${projectName}AsSwiftPackageFrom${xcframeworkTaskName}`. For example:
```shell
./gradlew :shared:deploySharedAsSwiftPackageFromAssembleSharedDebugXCFramework
```

### <a name="spm-single-repo"></a> Repository with Package and XCFramework

This is the simplest deployment option that uses a single git repository to deploy the `Package.swift` and the XCFramework:

```kotlin
kotlin {
    appleBinariesDeployment {
        deployAsSwiftPackage(
            swiftPackageConfiguration = SwiftPackageConfiguration(
                packageDeployment = SPMPackageDeployment.GitDeployment(
                    repository = "git@github.com:foo/bar.git"
                ),
            ),
            xcframeworkDeployment = SPMXCFrameworkDeployment.GitDeployment()
        )
    }
}
```

Beware that the `git clone` time, repository size and consequently Swift Package resolution time will grow per new XCFramework deployment.

### <a name="spm-http-xcframework"></a> XCFramework in an HTTP file storage

This deployment option is suitable if you want to serve your XCFramework from an HTTP storage such as Artifactory Generic or Nexus Raw repositories:

```kotlin
kotlin {
    appleBinariesDeployment {
        deployAsSwiftPackage(
            swiftPackageConfiguration = SwiftPackageConfiguration(
                packageDeployment = SPMPackageDeployment.GitDeployment(
                    repository = "git@github.com:foo/bar.git"
                ),
            ),
            xcframeworkDeployment = SPMXCFrameworkDeployment.HttpDeployment(
                deployment = HttpStorageDeployment.Upload(
                    username = "admin",
                    password = "12345",
                    uploadDirectoryUrl = "https://jfrog.com/artifactory/generic/xcframeworks"
                )
            )
        )
    }
}
```

XCFramework is going to be uploaded with a PUT request to `https://jfrog.com/artifactory/generic/xcframeworks` at a versioned URL and the `Package.swift` file will reference this URL.

### <a name="spm-gh-packages-xcframework"></a> XCFramework in GitHub Packages

This deployment option is convenient if you want to use GitHub Packages as a storage for the XCFramework. For `token` use a [personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) with `packages:write` access or a [GITHUB_TOKEN](https://docs.github.com/en/actions/security-guides/automatic-token-authentication#about-the-github_token-secret).

```kotlin
kotlin {
    appleBinariesDeployment {
        deployAsSwiftPackage(
            swiftPackageConfiguration = SwiftPackageConfiguration(
                packageDeployment = SPMPackageDeployment.GitDeployment(
                    repository = "git@github.com:foo/bar.git"
                ),
            ),
            xcframeworkDeployment = SPMXCFrameworkDeployment.HttpDeployment(
                deployment = HttpStorageDeployment.GithubMavenPackage(
                    // https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens
                    token = "ghp_AaBbCc...",
                    // or in a CI workflow
                    token = System.getenv("GITHUB_TOKEN"),
                    username = "foo",
                    repository = "bar"
                )
            )
        )
    }
}
```

Using a Swift Package with an XCFramework in GitHub Packages will require you to set up [netrc or Keychain](#xcframework_download_auth).

### <a name="spm-gh-releases-xcframework"></a> XCFramework in GitHub Releases

GitHub Releases is similar to GitHub Packages:
```kotlin
deployment = HttpStorageDeployment.GithubReleases(
    // same as HttpStorageDeployment.GithubMavenPackage
)
```

GitHub Releases only works if your repository is public, but doesn't require netrc or Keychain set up on the consumer's side.

## <a name="cocoapods"></a> CocoaPods

Before specifying a deployment option, apply the `kotlin("native.cocoapods")` plugin.

```kotlin
plugins { 
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("store.kmpd.plugin")
}

kotlin { 
    cocoapods {
        // This version will be overwritten during deployment
        version = "1.0.0"
    }

    appleBinariesDeployment {
        // Deployments configured here will use the CocoaPods generated XCFramework declared above
    }
}
```

### <a name="cocoapods-single-repo"></a> Git repository with podspec and XCFramework

This is the simplest deployment option that uses a single git repository to deploy the podspec and the XCFramework:

```kotlin
kotlin {
    appleBinariesDeployment {
        deployAsCocoaPodsSpec(
            podspecDeployment = CocoaPodsSpecDeployment.GitRepository(
                repository = "git@github.com:foo/bar.git"
            ),
            xcframeworkDeployment = CocoaPodsXCFrameworkDeployment.GitDeployment()
        )
    }
}
```

In the Podfile you can point directly to the deployment repository, specifying version in the tag: 

```ruby
pod 'shared', :git => 'git@github.com:foo/bar.git', :tag => '0.0.123'
```

### <a name="cocoapods-specs-repo"></a> Specs repository

This is the standard deployment option recommended by CocoaPods. The podspec file is deployed to a repository with a specific layout and references the :

```kotlin
kotlin {
    appleBinariesDeployment {
        deployAsCocoaPodsSpec(
            podspecDeployment = CocoaPodsSpecDeployment.SpecRepository(
                repository = "git@github.com:foo/bar.git",
            ),
            xcframeworkDeployment = CocoaPodsXCFrameworkDeployment.HttpDeployment(
                deployment = HttpStorageDeployment.Upload(
                    username = "admin",
                    password = "12345",
                    uploadDirectoryUrl = "https://jfrog.com/artifactory/generic/xcframeworks"
                )
            )
        )
    }
}
```

In the Podfile you can specify the dependency as follows:

```ruby
source 'git@github.com:foo/bar.git'

# Or specify the source directly in the pod:
pod 'shared', :source => 'git@github.com:foo/bar.git'
```

You might need to update specs repository via `pod repo update` before running `pod install`. When updating to a new version you will also need to run `pod update shared`.

### <a name="cocoapods-http"></a> Podspec and XCFramework in HTTP file storage, GitHub Packages and GitHub releases

With these deployment options your podspec and the XCFramework will be deployed to an HTTP storage, so you don't need an additional git repository. The downside of this option is that your `podspec.json` file must be accessible to all consumers on the network without authentication:

```kotlin
kotlin {
    appleBinariesDeployment {
        deployAsCocoaPodsSpec(
            podspecDeployment = CocoaPodsSpecDeployment.HttpDeployment(
                deployment = HttpStorageDeployment.Upload(
                    username = "admin",
                    password = "12345",
                    uploadDirectoryUrl = "https://jfrog.com/artifactory/generic/podspecs"
                )
            ),
            xcframeworkDeployment = CocoaPodsXCFrameworkDeployment.HttpDeployment(
                deployment = HttpStorageDeployment.Upload(
                    username = "admin",
                    password = "12345",
                    uploadDirectoryUrl = "https://jfrog.com/artifactory/generic/xcframeworks"
                )
            )
        )
    }
}
```

In the Podfile you can specify the dependency as follows:

```ruby
# CocoaPods doesn't support authentication for ":podspec" parameter; the podspec URL must be accessible without authentication
pod 'shared', :podspec => 'https://jfrog.com/artifactory/generic/podspecs/shared-123.podspec.json'
```

# <a name="xcframework_download_auth"></a> Authenticating XCFramework downloads

Serving your XCFramework over HTTP file storage with authentication will require additional set up on the consumer's side.

## .netrc

CocoaPods and Swift Package Manager can read [.netrc](https://everything.curl.dev/usingcurl/netrc.html) to authenticate the request when downloading the XCFramework:

```
machine maven.pkg.github.com
login token
password ghp_AaBbCc
```

## Keychain

Swift Package Manager can also read tokens [from the Keychain](https://github.com/apple/swift-package-manager/issues/7236#issuecomment-1899012631). An equivalent to the `.netrc` configuration with Keychain is the following:

```shell
security add-internet-password -a token -s maven.pkg.github.com -r htps -w ghp_AaBbCc
```