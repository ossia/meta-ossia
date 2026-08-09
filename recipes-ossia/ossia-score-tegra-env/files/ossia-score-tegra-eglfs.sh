# Only the eglfs QPA reads this. Under vkkhrdisplay, wayland or xcb it is
# ignored, so selecting one of those needs no change here.
export QT_QPA_EGLFS_INTEGRATION=eglfs_kms
