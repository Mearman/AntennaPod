#!/bin/bash
FIRED=0
adb -s emulator-5594 logcat -s StaleTeardownRepro:I -v time | while read -r line; do
  case "$line" in
    *"ready to relaunch"*)
      if [ "$FIRED" -lt 2 ]; then
        FIRED=$((FIRED+1))
        sleep 0.6
        adb -s emulator-5594 shell am start -n de.danoeh.antennapod.debug/de.danoeh.antennapod.activity.MainActivity
      fi
      ;;
  esac
done
