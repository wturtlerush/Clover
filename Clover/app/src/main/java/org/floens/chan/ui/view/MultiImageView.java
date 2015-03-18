/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.view;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.koushikdutta.async.future.Future;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.FileCache;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class MultiImageView extends LoadView implements View.OnClickListener {
    public enum Mode {
        UNLOADED, LOWRES, BIGIMAGE
    }

    private static final String TAG = "MultiImageView";

    private PostImage postImage;
    private Callback callback;

    private Mode mode = Mode.UNLOADED;

    private boolean thumbnailNeeded = true;
    private Future<?> request;

    private VideoView videoView;

    public MultiImageView(Context context) {
        super(context);
        init();
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnClickListener(this);
    }

    public void bindPostImage(PostImage postImage, Callback callback) {
        this.postImage = postImage;
        this.callback = callback;
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            if (mode == Mode.LOWRES) {
                Logger.d(TAG, "Changing mode to LOWRES for " + postImage.thumbnailUrl);
                AndroidUtils.waitForMeasure(this, new AndroidUtils.OnMeasuredCallback() {
                    @Override
                    public boolean onMeasured(View view) {
                        setThumbnail(postImage.thumbnailUrl);
                        return false;
                    }
                });
            } else if (mode == Mode.BIGIMAGE) {
                Logger.d(TAG, "Changing mode to BIGIMAGE for " + postImage.thumbnailUrl);
                // Always done after at least LOWRES, so the view is measured
                if (postImage.type == PostImage.Type.STATIC) {
                    setBigImage(postImage.imageUrl);
                } else {
                    Logger.e(TAG, "postImage type not STATIC, not changing to BIGIMAGE mode!");
                }
            }
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setThumbnail(String thumbnailUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        // Also use volley for the thumbnails
        ChanApplication.getVolleyImageLoader().get(thumbnailUrl, new com.android.volley.toolbox.ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onError();
            }

            @Override
            public void onResponse(ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null && thumbnailNeeded) {
                    ImageView thumbnail = new ImageView(getContext());
                    thumbnail.setImageBitmap(response.getBitmap());
                    thumbnail.setLayoutParams(AndroidUtils.MATCH_PARAMS);
                    setView(thumbnail, false);
                    callback.onModeLoaded(MultiImageView.this, Mode.LOWRES);
                }
            }
        }, getWidth(), getHeight());
    }

    public void setBigImage(String imageUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading big image");
            return;
        }

        callback.setProgress(this, true);
        request = ChanApplication.getFileCache().downloadFile(getContext(), imageUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
//                    callback.setLinearProgress(0, 0, true);
                    thumbnailNeeded = false;
                } else {
                    callback.setLinearProgress(MultiImageView.this, downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                setBigImageFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setBigImageFile(File file) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImageFile(file.getAbsolutePath());
        image.setOnClickListener(MultiImageView.this);

        addView(image);

        image.setInitCallback(new CustomScaleImageView.InitedCallback() {
            @Override
            public void onInit() {
                removeAllViews();
                addView(image);
                callback.setProgress(MultiImageView.this, false);
                callback.onModeLoaded(MultiImageView.this, Mode.BIGIMAGE);
            }

            @Override
            public void onOutOfMemory() {
                onOutOfMemoryError();
            }
        });
    }

    public void setGif(String gifUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        callback.setProgress(this, true);
        request = ChanApplication.getFileCache().downloadFile(getContext(), gifUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
                    callback.setProgress(MultiImageView.this, false);
                    callback.setLinearProgress(MultiImageView.this, 0, 0, true);
                    thumbnailNeeded = false;
                } else {
                    callback.setLinearProgress(MultiImageView.this, downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                setGifFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setGifFile(File file) {
        GifDrawable drawable;
        try {
            drawable = new GifDrawable(file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            onError();
            return;
        } catch (OutOfMemoryError e) {
            System.gc();
            e.printStackTrace();
            onOutOfMemoryError();
            return;
        }

        GifImageView view = new GifImageView(getContext());
        view.setImageDrawable(drawable);
        view.setLayoutParams(AndroidUtils.MATCH_PARAMS);
        setView(view, false);
    }

    public void setVideo(String videoUrl) {
        callback.setProgress(this, true);
        request = ChanApplication.getFileCache().downloadFile(getContext(), videoUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
                    callback.setProgress(MultiImageView.this, false);
                    callback.setLinearProgress(MultiImageView.this, 0, 0, true);
                    thumbnailNeeded = false;
                } else {
                    callback.setLinearProgress(MultiImageView.this, downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                setVideoFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setVideoFile(final File file) {
        if (ChanSettings.getVideoExternal()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "video/*");

            try {
                getContext().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(), R.string.open_link_failed, Toast.LENGTH_SHORT).show();
            }
        } else {
            Context proxyContext = new NoMusicServiceCommandContext(getContext());

            videoView = new VideoView(proxyContext);
            videoView.setZOrderOnTop(true);
            videoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            videoView.setLayoutParams(AndroidUtils.MATCH_PARAMS);
            LayoutParams par = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            par.gravity = Gravity.CENTER;
            videoView.setLayoutParams(par);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    callback.onVideoLoaded(MultiImageView.this);
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    callback.onVideoError(MultiImageView.this, file);

                    return true;
                }
            });

            videoView.setVideoPath(file.getAbsolutePath());

            setView(videoView, false);

            videoView.start();
        }
    }

    public VideoView getVideoView() {
        return videoView;
    }

    public void onError() {
        Toast.makeText(getContext(), R.string.image_preview_failed, Toast.LENGTH_SHORT).show();
        callback.setProgress(this, false);
    }

    public void onNotFoundError() {
        Toast.makeText(getContext(), R.string.image_not_found, Toast.LENGTH_LONG).show();
        callback.setProgress(this, false);
    }

    public void onOutOfMemoryError() {
        Toast.makeText(getContext(), R.string.image_preview_failed_oom, Toast.LENGTH_SHORT).show();
        callback.setProgress(this, false);
    }

    public void cancelLoad() {
        if (request != null) {
            request.cancel(true);
        }
    }

    @Override
    public void onClick(View v) {
        callback.onTap(this);
    }

    public static interface Callback {
        public void onTap(MultiImageView multiImageView);

        public void setProgress(MultiImageView multiImageView, boolean progress);

        public void setLinearProgress(MultiImageView multiImageView, long current, long total, boolean done);

        public void onVideoLoaded(MultiImageView multiImageView);

        public void onVideoError(MultiImageView multiImageView, File video);

        public void onModeLoaded(MultiImageView multiImageView, Mode mode);
    }

    public static class NoMusicServiceCommandContext extends ContextWrapper {
        public NoMusicServiceCommandContext(Context base) {
            super(base);
        }

        @Override
        public void sendBroadcast(Intent intent) {
            // Only allow broadcasts when it's not a music service command
            // Prevents pause intents from broadcasting
            if (!"com.android.music.musicservicecommand".equals(intent.getAction())) {
                super.sendBroadcast(intent);
            }
        }
    }
}
