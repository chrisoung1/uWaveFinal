package com.example.leonardgomez.uwavefinal;

import android.content.ComponentName;
import android.content.Context;
import android.media.browse.MediaBrowser;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class LivePlayer extends MainActivity {

    private ImageView mAlbumArt;
    private TextView mTitleTextView;
    private TextView mArtistTextView;
    private ImageButton playStopButton;

    private MediaBrowserCompat mMediaBrowser;

    private boolean isPlaying;

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallbacks = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {

            // Get the token for the MediaSession
            MediaSessionCompat.Token token = mMediaBrowser.getSessionToken();

            // Create a MediaControllerCompat
            MediaControllerCompat mediaController =
                    new MediaControllerCompat(LivePlayer.this, // Context
                            token);

            // Save the controller
            MediaControllerCompat.setMediaController(LivePlayer.this, mediaController);

            // Finish building the UI
            buildTransportControls();
        }

        @Override
        public void onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
        }

        @Override
        public void onConnectionFailed() {
            // The Service has refused our connection
        }
    };

    private final MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {}

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {}
            };

    void buildTransportControls() {
        // Grab the view for the play/pause button
        playStopButton = (ImageButton) findViewById(R.id.playButton);

        // Attach a listener to the button
        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Since this is a play/pause button, you'll need to test the current state
                // and choose the action accordingly

                int pbState = MediaControllerCompat.getMediaController(LivePlayer.this).getPlaybackState().getState();
                if (pbState == PlaybackStateCompat.STATE_PLAYING) {
                    MediaControllerCompat.getMediaController(LivePlayer.this).getTransportControls().stop();
                } else {
                    MediaControllerCompat.getMediaController(LivePlayer.this).getTransportControls().play();
                }
            }

            MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(LivePlayer.this);

            // Display the initial state
            MediaMetadataCompat metadata = mediaController.getMetadata();
            PlaybackStateCompat pbState = mediaController.getPlaybackState();

            // Register a Callback to stay in sync
            mediaController.registerCallback(controllerCallback);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_player);

        //Create MediaBrowserServiceCompat
        mMediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, MediaBrowserService.class), mConnectionCallbacks,
                null);

        // Construct menu
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        fetchSongData song = new fetchSongData();
        song.execute();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        // (see "stay in sync with the MediaSession")
        if (MediaControllerCompat.getMediaController(LivePlayer.this) != null) {
            MediaControllerCompat.getMediaController(LivePlayer.this).unregisterCallback(controllerCallback);
        }
        mMediaBrowser.disconnect();

    }

    class fetchSongData extends AsyncTask<Void, Void, Void> {
        String data = "";
        TextView songName = (TextView)findViewById(R.id.songName);
        TextView artistAndAlbumName = (TextView)findViewById(R.id.artistAndAlbum);
        String song = "";
        String artistAndAlbum = "";

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                URL url = new URL("https://uwave.fm/listen/now-playing.json");
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = httpURLConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line = "";
                while(line != null) {
                    line = bufferedReader.readLine();
                    if(line != null) {
                        data += line;
                    }
                }

                try {
                    JSONObject songData = new JSONObject(data);
                    song = songData.getString("title");
                    if(songData.getString("artist").equals("") && songData.getString("album").equals("")) {
                        artistAndAlbumName.setVisibility(View.INVISIBLE);
                    }else artistAndAlbum = songData.getString("artist") + " - " + songData.getString("album");
                }catch (JSONException e) {
                    e.printStackTrace();
                }
            }catch (MalformedURLException e) {
                e.printStackTrace();
            }catch (IOException e) {
                e.printStackTrace();
                throw new Error("Failed.");
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            songName.setText(song);
            artistAndAlbumName.setText(artistAndAlbum);
        }
    }

    /**
     * Customize the connection to our {@link android.support.v4.media.MediaBrowserServiceCompat}
     * and implement our app specific desires.
     */
    private class MediaBrowserConnection extends MediaBrowserHelper {
        private MediaBrowserConnection(Context context) {
            super(context, MediaBrowserService.class);
        }

        @Override
        protected void onConnected(@NonNull MediaControllerCompat mediaController) {
            //Get token for the MediaSession
            MediaSessionCompat.Token token = mMediaBrowser.getSessionToken();

            //Create a MediaControllerCompat
            MediaControllerCompat mediaController = new MediaControllerCompat(LivePlayer.this, token);

            //Save the controller
            MediaControllerCompat.setMediaController(LivePlayer.this, mediaController);

            //Finish building the UI
            buildTransportControls();
        }

        @Override
        protected void onChildrenLoaded(@NonNull String parentId,
                                        @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);

            final MediaControllerCompat mediaController = getMediaController();

            // Queue up all media items for this simple sample.
            for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                mediaController.addQueueItem(mediaItem.getDescription());
            }

            // Call prepare now so pressing play just works.
            mediaController.getTransportControls().prepare();
        }
    }

    /**
     * Implementation of the {@link MediaControllerCompat.Callback} methods we're interested in.
     * <p>
     * Here would also be where one could override
     * {@code onQueueChanged(List<MediaSessionCompat.QueueItem> queue)} to get informed when items
     * are added or removed from the queue. We don't do this here in order to keep the UI
     * simple.
     */

    private class MediaBrowserListener extends MediaControllerCompat.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
            isPlaying = playbackState != null &&
                    playbackState.getState() == PlaybackStateCompat.STATE_PLAYING;
            playStopButton.setPressed(isPlaying);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat mediaMetadata) {
            if (mediaMetadata == null) {
                return;
            }
            mTitleTextView.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            mArtistTextView.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            mAlbumArt.setImageBitmap(MusicLibrary.getAlbumBitmap(
                    LivePlayer.this,
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)));
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
        }
    }
}