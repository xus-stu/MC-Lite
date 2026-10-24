Unicode true
!include "MUI2.nsh"
Icon "@ROOT@/assets/AppIcon.ico"
UninstallIcon "@ROOT@/assets/AppIcon.ico"

Name "MC Lite V@VERSION@"
OutFile "@ROOT@/dist/MC-Lite_v@VERSION@_Setup.exe"
InstallDir "$LOCALAPPDATA\Programs\MCLite"
InstallDirRegKey HKCU "Software\MCLite" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma

!define MUI_ABORTWARNING
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "SimpChinese"

Section "MC Lite" SecMain
  SetShellVarContext current
  SetOutPath "$INSTDIR"
  Delete "$INSTDIR\MCLite_v*.jar"
  File /r "@ROOT@/build/installer/MC-Lite/*"
  WriteRegStr HKCU "Software\MCLite" "InstallDir" "$INSTDIR"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\MC Lite"
  Delete "$DESKTOP\MC Lite*.lnk"
  Delete "$SMPROGRAMS\MC Lite\MC Lite*.lnk"
  CreateShortCut "$SMPROGRAMS\MC Lite\MC Lite V@VERSION@.lnk" "$INSTDIR\runtime\bin\javaw.exe" "-jar $\"$INSTDIR\MCLite_v@VERSION@.jar$\""
  CreateShortCut "$DESKTOP\MC Lite V@VERSION@.lnk" "$INSTDIR\runtime\bin\javaw.exe" "-jar $\"$INSTDIR\MCLite_v@VERSION@.jar$\""
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MCLite" "DisplayName" "MC Lite V@VERSION@"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MCLite" "DisplayVersion" "@VERSION@"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MCLite" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MCLite" "InstallLocation" "$INSTDIR"
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  Delete "$DESKTOP\MC Lite V@VERSION@.lnk"
  RMDir /r "$SMPROGRAMS\MC Lite"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MCLite"
  DeleteRegKey HKCU "Software\MCLite"
SectionEnd
