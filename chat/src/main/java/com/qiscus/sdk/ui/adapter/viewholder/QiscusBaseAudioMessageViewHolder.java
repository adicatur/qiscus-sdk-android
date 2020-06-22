/*
 * Copyright (c) 2016 Qiscus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qiscus.sdk.ui.adapter.viewholder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.qiscus.sdk.Qiscus;
import com.qiscus.sdk.chat.core.data.model.QiscusComment;
import com.qiscus.sdk.ui.adapter.OnItemClickListener;
import com.qiscus.sdk.ui.adapter.OnLongItemClickListener;
import com.qiscus.sdk.ui.view.QiscusProgressView;

/**
 * Created on : September 27, 2016
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
public abstract class QiscusBaseAudioMessageViewHolder extends QiscusBaseMessageViewHolder<QiscusComment>
        implements QiscusComment.ProgressListener, QiscusComment.DownloadingListener, QiscusComment.PlayingAudioListener {

    @NonNull
    protected ImageView playButton;
    @NonNull
    protected AppCompatSeekBar seekBar;
    @NonNull
    protected TextView durationView;
    @Nullable
    protected QiscusProgressView progressView;

    protected int playIcon;
    protected int pauseIcon;

    private QiscusComment qiscusComment;

    public QiscusBaseAudioMessageViewHolder(View itemView, OnItemClickListener itemClickListener,
                                            OnLongItemClickListener longItemClickListener) {
        super(itemView, itemClickListener, longItemClickListener);
        playButton = getPlayButton(itemView);
        seekBar = getSeekBar(itemView);
        durationView = getDurationView(itemView);
        progressView = getProgressView(itemView);
        seekBar.setOnTouchListener((v, event) -> true);
    }

    @NonNull
    protected abstract ImageView getPlayButton(View itemView);

    @NonNull
    protected abstract AppCompatSeekBar getSeekBar(View itemView);

    @NonNull
    protected abstract TextView getDurationView(View itemView);

    @Nullable
    protected abstract QiscusProgressView getProgressView(View itemView);

    @Override
    protected void loadChatConfig() {
        super.loadChatConfig();
        playIcon = Qiscus.getChatConfig().getPlayAudioIcon();
        pauseIcon = Qiscus.getChatConfig().getPauseAudioIcon();
    }

    @Override
    public void bind(QiscusComment qiscusComment) {
        super.bind(qiscusComment);
        this.qiscusComment = qiscusComment;
        qiscusComment.setProgressListener(this);
        qiscusComment.setDownloadingListener(this);
        qiscusComment.setPlayingAudioListener(this);
        setUpPlayButton(qiscusComment);
        showProgressOrNot(qiscusComment);
    }

    protected void setUpPlayButton(QiscusComment qiscusComment) {
        playButton.setImageResource(qiscusComment.isPlayingAudio() ? pauseIcon : playIcon);
    }

    protected void showProgressOrNot(QiscusComment qiscusComment) {
        if (progressView != null) {
            progressView.setProgress(qiscusComment.getProgress());
            progressView.setVisibility(
                    qiscusComment.isDownloading()
                            || qiscusComment.getState() == QiscusComment.STATE_PENDING
                            || qiscusComment.getState() == QiscusComment.STATE_SENDING
                            ? View.VISIBLE : View.GONE
            );
        }
    }

    @Override
    protected void setUpColor() {
        if (progressView != null) {
            progressView.setFinishedColor(messageFromMe ? rightBubbleColor : leftBubbleColor);
            progressView.setUnfinishedColor(messageFromMe ? rightBubbleColor : leftBubbleColor);
        }
        super.setUpColor();
    }

    @Override
    protected void showMessage(QiscusComment qiscusComment) {
        playButton.setOnClickListener(v -> playAudio(qiscusComment));
        seekBar.setMax(qiscusComment.getAudioDuration());
        seekBar.setProgress(qiscusComment.isPlayingAudio() ? qiscusComment.getCurrentAudioPosition() : 0);
        setTimeRemaining(qiscusComment.isPlayingAudio() ?
                qiscusComment.getAudioDuration() - qiscusComment.getCurrentAudioPosition()
                : qiscusComment.getAudioDuration());
    }

    @Override
    public void onProgress(QiscusComment qiscusComment, int percentage) {
        if (progressView != null) {
            progressView.setProgress(percentage);
        }
    }

    @Override
    public void onDownloading(QiscusComment qiscusComment, boolean downloading) {
        if (progressView != null) {
            progressView.setVisibility(downloading ? View.VISIBLE : View.GONE);
        }
    }

    protected void playAudio(QiscusComment qiscusComment) {
        if (qiscusComment.getAudioDuration() > 0) {
            qiscusComment.playAudio();
        } else {
            onClick(messageBubbleView);
        }
    }

    private void setTimeRemaining(long duration) {
        durationView.setText(DateUtils.formatElapsedTime(duration / 1000));
    }

    @Override
    public void onPlayingAudio(QiscusComment qiscusComment, int currentPosition) {
        if (qiscusComment.equals(this.qiscusComment)) {
            playButton.setImageResource(pauseIcon);
            seekBar.setProgress(currentPosition);
            setTimeRemaining(qiscusComment.getAudioDuration() - currentPosition);
        }
    }

    @Override
    public void onPauseAudio(QiscusComment qiscusComment) {
        if (qiscusComment.equals(this.qiscusComment)) {
            playButton.setImageResource(playIcon);
        }
    }

    @Override
    public void onStopAudio(QiscusComment qiscusComment) {
        if (qiscusComment.equals(this.qiscusComment)) {
            playButton.setImageResource(playIcon);
            seekBar.setProgress(0);
            setTimeRemaining(qiscusComment.getAudioDuration());
        }
    }
}
