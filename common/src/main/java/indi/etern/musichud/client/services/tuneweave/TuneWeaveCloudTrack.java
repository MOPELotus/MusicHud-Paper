package indi.etern.musichud.client.services.tuneweave;

import indi.etern.musichud.beans.music.MusicDetail;

public record TuneWeaveCloudTrack(String reference, MusicDetail track, String filename,
                                  long fileSize, String fileType, long bitrate, String md5,
                                  String addedAt, String matchedTrackReference) {
}
