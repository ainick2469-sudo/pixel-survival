Option Explicit

Dim shell, fso
Dim scriptDir, repoDir, toolsDir, javaHome, javawPath, gradlePath, buildDir, logPath, jarPath
Dim buildCommand, exitCode, previousDirectory, attempt
Dim buildOnly

Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
repoDir = fso.GetAbsolutePathName(scriptDir & "\..")
toolsDir = fso.GetAbsolutePathName(repoDir & "\..\pixel-survival-tools")
javaHome = toolsDir & "\jdk-21.0.10+7"
javawPath = javaHome & "\bin\javaw.exe"
gradlePath = repoDir & "\gradlew.bat"
buildDir = repoDir & "\build\launcher"
logPath = buildDir & "\desktop-launch.log"
jarPath = repoDir & "\build\libs\pixel-survival-desktop.jar"
buildOnly = False

If WScript.Arguments.Count > 0 Then
    buildOnly = (LCase(WScript.Arguments.Item(0)) = "/buildonly")
End If

If Not fso.FileExists(javawPath) Then
    MsgBox "Java 21 was not found at:" & vbCrLf & javawPath, vbCritical, "Pixel Survival Launcher"
    WScript.Quit 1
End If

If Not fso.FileExists(gradlePath) Then
    MsgBox "gradlew.bat was not found at:" & vbCrLf & gradlePath, vbCritical, "Pixel Survival Launcher"
    WScript.Quit 1
End If

If Not fso.FolderExists(buildDir) Then
    fso.CreateFolder(buildDir)
End If

buildCommand = "cmd.exe /c " & Quote("cd /d " & Quote(repoDir) & _
    " && set ""JAVA_HOME=" & javaHome & """" & _
    " && set ""PATH=" & javaHome & "\bin;%PATH%""" & _
    " && gradlew.bat shadowJar --quiet --warning-mode none > " & Quote(logPath) & " 2>&1")

exitCode = shell.Run(buildCommand, 0, True)
If exitCode <> 0 Then
    shell.Run "notepad.exe " & Quote(logPath), 1, False
    MsgBox "Pixel Survival failed to build. The launcher log has been opened:" & vbCrLf & logPath, vbCritical, "Pixel Survival Launcher"
    WScript.Quit exitCode
End If

If buildOnly Then
    WScript.Quit 0
End If

On Error Resume Next
previousDirectory = shell.CurrentDirectory
shell.CurrentDirectory = repoDir
shell.Run Quote(javawPath) & " -Dfile.encoding=UTF-8 -jar " & Quote(jarPath), 1, False
shell.CurrentDirectory = previousDirectory
If Err.Number <> 0 Then
    MsgBox "Pixel Survival failed to start." & vbCrLf & "Launcher error: " & Err.Description, vbCritical, "Pixel Survival Launcher"
    WScript.Quit 1
End If
On Error GoTo 0

For attempt = 1 To 20
    WScript.Sleep 350
    On Error Resume Next
    If shell.AppActivate("Pixel Survival") Then
        WScript.Sleep 90
        shell.AppActivate("Pixel Survival")
        Exit For
    End If
    On Error GoTo 0
Next

Function Quote(value)
    Quote = Chr(34) & value & Chr(34)
End Function
