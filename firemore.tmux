tmux has-session -t firemore
if [ $? != 0 ]
then
     tmux new-session -s firemore -n editor -d
     tmux new-window  -n cryogen -t firemore
     tmux new-window -n server -t firemore
     tmux new-window -n console -t firemore
     tmux send-keys -t firemore:1 'cd ~/firemore' C-m
     tmux send-keys -t firemore:1 'emacs ./src/firemore/core.cljs ./test/firemore/core_test.cljs' C-m
     tmux send-keys -t firemore:2 'cd ~/firemore' C-m
     tmux send-keys -t firemore:2 'lein ring server' C-m
     tmux send-keys -t firemore:3 'cd ~/firemore' C-m
     tmux send-keys -t firemore:3 'python simple_http_server_cors.py' C-m
     tmux send-keys -t firemore:4 'cd ~/firemore' C-m
fi

tmux attach -t firemore