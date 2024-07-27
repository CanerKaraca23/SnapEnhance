use std::{ffi::CStr, fs};

use crate::{config, def_hook, dobby_hook_sym};

def_hook!(
    open_hook,
    i32,
    |path: *const u8, flags: i32| {
        let mut path = path;
        
        if let Ok(pathname) = CStr::from_ptr(path).to_str() {
            if pathname == "/system/fonts/NotoColorEmoji.ttf"  {
                if let Some(font_path) = config::native_config().custom_emoji_font_path {
                    if fs::metadata(&font_path).is_ok() {
                        path = (font_path.to_owned() + "\0").as_ptr();
                        debug!("open {}", font_path);
                    } else {
                        warn!("custom emoji font path does not exist: {}", font_path);
                    }
                }
            }
        }

        open_hook_original.unwrap()(path, flags)
    }
);


pub fn init() {
    dobby_hook_sym!("libc.so", "open", open_hook);
}