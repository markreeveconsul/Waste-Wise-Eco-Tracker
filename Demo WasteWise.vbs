Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
folder = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = folder
result = shell.Run("powershell.exe -NoProfile -ExecutionPolicy Bypass -File """ & folder & "\run.ps1"" -Demo", 0, True)
If result <> 0 Then MsgBox "WasteWise could not start. Open PowerShell in this folder and run .\run.ps1 -Demo to see the error.", 16, "WasteWise"
