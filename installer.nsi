; Game Ready Toolkit — Windows Installer
; Build with: makensis installer.nsi
; Requires NSIS 3.x  (https://nsis.sourceforge.io)

!define APP_NAME      "Game Ready Toolkit"
!define APP_VERSION   "1.1.0"
!define APP_EXE       "GameReadyToolkit.jar"
!define LAUNCH_BAT    "launch.bat"
!define INSTALL_DIR   "$PROGRAMFILES64\GameReadyToolkit"
!define REG_UNINST    "Software\Microsoft\Windows\CurrentVersion\Uninstall\GameReadyToolkit"

Name              "${APP_NAME} ${APP_VERSION}"
OutFile           "GameReadyToolkit-${APP_VERSION}-setup.exe"
InstallDir        "${INSTALL_DIR}"
InstallDirRegKey  HKLM "${REG_UNINST}" "InstallLocation"
RequestExecutionLevel admin
SetCompressor     /SOLID lzma

;------ Pages ----------------------------------------------------------------
!include "MUI2.nsh"
!define MUI_ABORTWARNING
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

;------ Installer section ----------------------------------------------------
Section "Install"
    SetOutPath "${INSTALL_DIR}"

    ; Copy JAR and launcher
    File "target\${APP_EXE}"
    File "${LAUNCH_BAT}"

    ; Start Menu shortcut
    CreateDirectory "$SMPROGRAMS\${APP_NAME}"
    CreateShortcut  "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" \
                    "$WINDIR\System32\cmd.exe" \
                    '/c "${INSTALL_DIR}\${LAUNCH_BAT}"' \
                    "$WINDIR\System32\cmd.exe" 0 SW_SHOWMINIMIZED

    ; Desktop shortcut
    CreateShortcut  "$DESKTOP\${APP_NAME}.lnk" \
                    "$WINDIR\System32\cmd.exe" \
                    '/c "${INSTALL_DIR}\${LAUNCH_BAT}"' \
                    "$WINDIR\System32\cmd.exe" 0 SW_SHOWMINIMIZED

    ; Write Programs & Features registry keys
    WriteRegStr   HKLM "${REG_UNINST}" "DisplayName"      "${APP_NAME}"
    WriteRegStr   HKLM "${REG_UNINST}" "DisplayVersion"   "${APP_VERSION}"
    WriteRegStr   HKLM "${REG_UNINST}" "Publisher"        "mayson208"
    WriteRegStr   HKLM "${REG_UNINST}" "InstallLocation"  "${INSTALL_DIR}"
    WriteRegStr   HKLM "${REG_UNINST}" "UninstallString"  '"${INSTALL_DIR}\Uninstall.exe"'
    WriteRegDWORD HKLM "${REG_UNINST}" "NoModify"         1
    WriteRegDWORD HKLM "${REG_UNINST}" "NoRepair"         1
    WriteRegStr   HKLM "${REG_UNINST}" "URLInfoAbout"     "https://github.com/mayson208/Game-Ready-Toolkit"

    WriteUninstaller "${INSTALL_DIR}\Uninstall.exe"
SectionEnd

;------ Uninstaller section --------------------------------------------------
Section "Uninstall"
    Delete "${INSTALL_DIR}\${APP_EXE}"
    Delete "${INSTALL_DIR}\${LAUNCH_BAT}"
    Delete "${INSTALL_DIR}\Uninstall.exe"
    RMDir  "${INSTALL_DIR}"

    Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
    RMDir  "$SMPROGRAMS\${APP_NAME}"
    Delete "$DESKTOP\${APP_NAME}.lnk"

    DeleteRegKey HKLM "${REG_UNINST}"
SectionEnd
