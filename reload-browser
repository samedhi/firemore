#!/bin/sh
# reload-browser - A cross-platform wrapper for reloading the current
# browser tab
# Eric Radman, 2014
# http://eradman.com/entrproject/scripts/

usage() {
	case `uname` in
	Darwin)
		# applescript needs the exact title
		echo "Usage: $(basename $0) Firefox [Safari \"Google Chrome\" ...]"
		;;
	*)
		# xdotool uses regular expressions
		echo "Usage: $(basename $0) Firefox [Chrome ...]"
		;;
	esac
	exit 1
}
[ $# -lt 1 ] && usage

for app in "$@"
do
	case `uname` in
	Darwin)
		/usr/bin/osascript <<-APPLESCRIPT
		set prev to (path to frontmost application as text)
		tell application "$app"
		    activate
		end tell
		delay 0.5
		tell application "System Events" to keystroke "r" using {command down}
		delay 0.5
		activate application prev
		APPLESCRIPT
		;;
	*)
		cw=$(xdotool getwindowfocus -f)
		xdotool search --onlyvisible --class "$app" windowfocus key \
		    --delay 100 --window %@ 'ctrl+r' || {
			1>&2 echo "unable to signal an application named \"$app\""
		}
		sleep 0.5
		xdotool windowfocus $cw
		;;
	esac
done
