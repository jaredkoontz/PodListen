package com.einmalfel.podlisten;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

/**
 * This class keeps records on sync errors and manages sync notification
 */
public class SyncState {
  private static final int NOTIFICATION_ID = 1;
  private final SyncResult syncResult;
  private final NotificationManagerCompat nm;
  private final NotificationCompat.Builder nb;
  private final Context context;
  private int maxFeeds = 0;
  private int errors = 0;
  private int parsed = 0;
  private int newEpisodes = 0;
  private boolean stopped = false;

  SyncState(@NonNull Context context, @NonNull SyncResult syncResult) {
    this.syncResult = syncResult;
    this.context = context;
    nm = NotificationManagerCompat.from(context);
    nb = new NotificationCompat.Builder(context);
    Intent intent = new Intent(context, MainActivity.class);
    intent.putExtra(MainActivity.PAGE_LAUNCH_OPTION, MainActivity.Pages.NEW_EPISODES.ordinal());
    PendingIntent pendingIntent = PendingIntent.getActivity(
        context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    nb.setSmallIcon(R.mipmap.ic_sync_green_24dp)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(pendingIntent);
  }

  synchronized void start(int maxFeeds) {
    this.maxFeeds = maxFeeds;
    nb.setContentTitle("Refreshing PodListen..")
      .setOngoing(true)
      .setAutoCancel(false)
      .setProgress(0, 0, true);
    updateNotification();
  }

  synchronized void error(String message) {
    nb.setOngoing(false)
      .setAutoCancel(true)
      .setProgress(0, 0, false)
      .setContentTitle("Refresh failed")
      .setContentText(message);
    updateNotification();
    stopped = true;
  }

  synchronized void stop() {
    Cursor cursor = context.getContentResolver().query(
        Provider.episodeUri,
        null,
        Provider.K_ESTATE + " == ?", new String[]{Integer.toString(Provider.ESTATE_NEW)},
        null,
        null);
    StringBuilder stringBuilder = new StringBuilder();
    if (cursor == null) {
      stringBuilder.append("DB error");
    } else {
      int count = cursor.getCount();
      cursor.close();
      stringBuilder.append("New episodes: ");
      if (count == newEpisodes) {
        stringBuilder.append(newEpisodes);
      } else {
        stringBuilder.append(count);
        if (newEpisodes > 0) {
          stringBuilder.append("(+")
                       .append(newEpisodes)
                       .append(")");
        }
      }
      if (parsed > 0) {
        stringBuilder.append(", Feeds loaded: ")
                     .append(parsed);
      }
      if (errors > 0) {
        stringBuilder.append(", Failed to load: ")
                     .append(errors);
      }
    }
    nb.setOngoing(false)
      .setAutoCancel(true)
      .setProgress(0, 0, false)
      .setContentTitle("Podlisten refreshed")
      .setContentText(stringBuilder);
    updateNotification();
    stopped = true;
  }

  synchronized private void updateProgress(String message) {
    nb.setProgress(maxFeeds, errors + parsed, false);
    nb.setContentText(message);
    updateNotification();
  }


  synchronized void signalParseError(String feedTitle) {
    syncResult.stats.numSkippedEntries++;
    errors++;
    updateProgress("Parsing failed: " + feedTitle);
  }

  synchronized void signalDBError(String feedTitle) {
    syncResult.databaseError = true;
    errors++;
    updateProgress("DB error: " + feedTitle);
  }

  synchronized void signalIOError(String feedTitle) {
    syncResult.stats.numIoExceptions++;
    errors++;
    updateProgress("IO error: " + feedTitle);
  }

  synchronized void signalFeedSuccess(@Nullable String feedTitle, int episodesAdded) {
    syncResult.stats.numUpdates++;
    parsed++;
    newEpisodes += episodesAdded;
    if (feedTitle != null) {
      updateProgress("Loaded: " + feedTitle);
    }
  }

  synchronized private void updateNotification() {
    if (!stopped) {
      nm.notify(NOTIFICATION_ID, nb.build());
    }
  }
}
