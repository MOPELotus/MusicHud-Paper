package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ProgressBar;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveSession;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.ArtistCard;
import indi.etern.musichud.client.ui.components.FlexWrapLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.PlatformSelector;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.pages.CloudView;
import indi.etern.musichud.client.ui.pages.PodcastRadioView;
import indi.etern.musichud.client.ui.pages.UniPlaylistView;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.interfaces.IClientLoginService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class AccountView extends LinearLayout {
    @Getter
    private static AccountView instance;
    private final IClientLoginService clientLoginService = LoginService.getInstance();
    private final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();
    private TuneWeavePlatform selectedPlatform = tuneWeave.defaultPlatform();
    private boolean showingUniPlaylists;
    private int refreshGeneration;
    private final Map<ElementKey, View> elementMap = new HashMap<>();
    private FlexWrapLayout myPlaylistCards;
    private FlexWrapLayout mySubscribedPlaylistCards;
    private FlexWrapLayout albumCards;
    private FlexWrapLayout artistCards;
    private LinearLayout myPlaylistsContent;
    private LinearLayout mySubscribedPlaylistsContent;
    private LinearLayout mySubscribedAlbumsContent;
    private LinearLayout mySubscribedArtistsContent;
    private Unregister playlistAddRegister;
    private Unregister playlistRemoveRegister;
    private Unregister albumAddRegister;
    private Unregister albumRemoveRegister;
    private Unregister artistAddRegister;
    private Unregister artistRemoveRegister;
    private final Consumer<Playlist> playlistCardCreator = playlist -> {
        MuiModApi.postToUiThread(() -> {
            if (!isAttachedToWindow()) {
                return;
            }
            long id = playlist.getId();
            elementMap.computeIfAbsent(new ElementKey(Playlist.class, id), (key) -> {
                MusicCollectionCard card = new MusicCollectionCard(getContext(), playlist);
                card.setTag(id);
                mySubscribedPlaylistCards.addView(card);
                return card;
            });
        });
    };
    private final Consumer<Album> albumCardCreator = album -> {
        MuiModApi.postToUiThread(() -> {
            if (!isAttachedToWindow()) {
                return;
            }
            long id = album.getId();
            elementMap.computeIfAbsent(new ElementKey(Album.class, id), (key) -> {
                MusicCollectionCard card = new MusicCollectionCard(getContext(), album);
                card.setTag(id);
                albumCards.addView(card);
                return card;
            });
        });
    };
    private final Consumer<Artist> artistCardCreator = artist -> {
        MuiModApi.postToUiThread(() -> {
            if (!isAttachedToWindow()) {
                return;
            }
            long id = artist.getId();
            elementMap.computeIfAbsent(new ElementKey(Artist.class, id), (key) -> {
                ArtistCard artistCard = new ArtistCard(getContext());
                artistCard.setTag(artist.getId());
                artistCard.bindData(artist);
                artistCards.addView(artistCard);
                return artistCard;
            });
        });
    };

    public AccountView(Context context) {
        super(context);
        if (!tuneWeave.hasCredential(selectedPlatform)) {
            for (TuneWeavePlatform platform : TuneWeavePlatform.values()) {
                if (tuneWeave.hasCredential(platform)) {
                    selectedPlatform = platform;
                    break;
                }
            }
        }
//        refresh(false);
        instance = this;
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                instance = AccountView.this;
                refresh(false);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                unregisterCollectionListeners();
                instance = null;
            }
        });
    }

    private void unregisterCollectionListeners() {
        if (playlistAddRegister != null) {
            playlistAddRegister.unregister();
            playlistAddRegister = null;
        }
        if (playlistRemoveRegister != null) {
            playlistRemoveRegister.unregister();
            playlistRemoveRegister = null;
        }
        if (albumAddRegister != null) {
            albumAddRegister.unregister();
            albumAddRegister = null;
        }
        if (albumRemoveRegister != null) {
            albumRemoveRegister.unregister();
            albumRemoveRegister = null;
        }
        if (artistAddRegister != null) {
            artistAddRegister.unregister();
            artistAddRegister = null;
        }
        if (artistRemoveRegister != null) {
            artistRemoveRegister.unregister();
            artistRemoveRegister = null;
        }
    }

    public void refresh(boolean ignoreCache) {
        int generation = ++refreshGeneration;
        unregisterCollectionListeners();
        removeAllViews();
        elementMap.clear();
        setOrientation(LinearLayout.VERTICAL);
        setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        Context context = getContext();

        TuneWeavePlatform activePlatform = tuneWeave.defaultPlatform();
        if (!tuneWeave.hasCredential(selectedPlatform) && tuneWeave.hasCredential(activePlatform)) {
            selectedPlatform = activePlatform;
        }

        LinearLayout platformTabs = new LinearLayout(context);
        platformTabs.setOrientation(LinearLayout.HORIZONTAL);
        platformTabs.setGravity(Gravity.CENTER);
        PlatformSelector platformSelector = new PlatformSelector(context, TuneWeavePlatform.values());
        platformSelector.setSelectedPlatform(selectedPlatform);
        platformSelector.setOnPlatformSelectedListener(platform -> {
            showingUniPlaylists = false;
            selectedPlatform = platform;
            tuneWeave.setDefaultPlatform(platform);
            LoginService.getInstance().switchTuneWeavePlatform(platform);
            refresh(false);
        });
        Image uniIcon = ImageUtils.getImageFromResource(
                "/assets/music_hud/textures/gui/icons/list_music.png");
        platformSelector.addAuxiliarySegment(uniIcon,
                I18n.get(MusicHud.MOD_ID + ".text.page.uniPlaylists"), showingUniPlaylists, () -> {
                    showingUniPlaylists = true;
                    refresh(false);
                });
        platformTabs.addView(platformSelector, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        LayoutParams platformTabsParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        platformTabsParams.setMargins(0, dp(16), 0, dp(8));
        addView(platformTabs, platformTabsParams);

        tuneWeave.setDefaultPlatform(selectedPlatform);
        if (showingUniPlaylists) {
            addView(new UniPlaylistView(context), new LayoutParams(MATCH_PARENT, 0, 1));
            return;
        }
        if (!tuneWeave.hasCredential(selectedPlatform)) {
            LoginView loginView = new LoginView(context);
            addView(loginView, new LayoutParams(MATCH_PARENT, 0, 1));
            return;
        }

        TuneWeaveSession sessionProfile = tuneWeave.cachedSession(selectedPlatform);
        String avatarUrl = sessionProfile != null && sessionProfile.avatarUrl() != null
                && !sessionProfile.avatarUrl().isBlank()
                ? sessionProfile.avatarUrl() : MusicHud.ICON_BASE64;
        String displayName = sessionProfile != null && sessionProfile.nickname() != null
                && !sessionProfile.nickname().isBlank()
                ? sessionProfile.nickname()
                : I18n.get(MusicHud.MOD_ID + ".platform." + selectedPlatform.apiName());
        String displayId = sessionProfile != null && sessionProfile.userId() != null
                && !sessionProfile.userId().isBlank()
                ? sessionProfile.userId() : "";
        setGravity(Gravity.TOP);
        LinearLayout topPanel = new LinearLayout(context);
        topPanel.setOrientation(LinearLayout.HORIZONTAL);
        topPanel.setGravity(Gravity.LEFT);

        UrlImageView avatar = new UrlImageView(context);
        avatar.setCircular(true);
        LayoutParams layoutParams = new LayoutParams(dp(68), dp(68));
        avatar.setLayoutParams(layoutParams);
        topPanel.addView(avatar);
        avatar.loadUrl(avatarUrl == null || avatarUrl.isBlank() ? MusicHud.ICON_BASE64 : avatarUrl);

        LayoutParams infoLp1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        infoLp1.setMargins(dp(16), 0, 0, 0);
        LinearLayout infoLayout = new LinearLayout(context);
        infoLayout.setOrientation(VERTICAL);
        infoLayout.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.addView(infoLayout, infoLp1);

        LayoutParams nameLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        TextView nickName = new TextView(context);
        nickName.setSingleLine(true);
        nickName.setTextSize(Theme.TEXT_SIZE_LARGER);
        nickName.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        nickName.setText(displayName);
        infoLayout.addView(nickName, nameLayoutParams);

        LayoutParams idLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        TextView id = new TextView(context);
        id.setSingleLine(true);
        id.setTextSize(Theme.TEXT_SIZE_NORMAL);
        id.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        id.setText(displayId);
        infoLayout.addView(id, idLayoutParams);

        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                .inset(0).cornerRadius(dp(4))
                .padding(new ButtonInsetBackgroundFactory.Padding(0, dp(2), 0, dp(2)))
                .build();

        LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.addView(buttonsLayout);

        Button refreshButton = new Button(context);
        refreshButton.setTextColor(Theme.PRIMARY_COLOR);
        refreshButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        refreshButton.setText(I18n.get(MusicHud.MOD_ID + ".button.refresh"));
        var background1 = backgroundFactory.newBackgroundDrawable();
        refreshButton.setBackground(background1);
        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, dp(8), 0);
        refreshButton.setLayoutParams(params);
        refreshButton.setOnClickListener(b -> {
            refresh(true);
        });
        buttonsLayout.addView(refreshButton);

        if (selectedPlatform != TuneWeavePlatform.BILIBILI) {
            Button managePlaylistsButton = new Button(context);
            managePlaylistsButton.setText(I18n.get(MusicHud.MOD_ID + ".button.managePlaylists"));
            managePlaylistsButton.setTextColor(Theme.PRIMARY_COLOR);
            managePlaylistsButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            managePlaylistsButton.setBackground(backgroundFactory.newBackgroundDrawable());
            managePlaylistsButton.setOnClickListener(button -> RouterContainer.getInstance().pushNavigate(
                    new PlatformPlaylistManagerView(context)));
            LayoutParams manageParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            manageParams.setMargins(0, 0, dp(8), 0);
            buttonsLayout.addView(managePlaylistsButton, manageParams);
        }

        if (TuneWeaveClientService.getInstance().defaultPlatform() == TuneWeavePlatform.NETEASE) {
            Button cloudButton = new Button(context);
            cloudButton.setText(I18n.get(MusicHud.MOD_ID + ".button.cloud"));
            cloudButton.setTextColor(Theme.PRIMARY_COLOR);
            cloudButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            cloudButton.setBackground(backgroundFactory.newBackgroundDrawable());
            cloudButton.setOnClickListener(button -> RouterContainer.getInstance().pushNavigate(new CloudView(context)));
            LayoutParams cloudParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            cloudParams.setMargins(0, 0, dp(8), 0);
            buttonsLayout.addView(cloudButton, cloudParams);

            Button programsButton = new Button(context);
            programsButton.setText(I18n.get(MusicHud.MOD_ID + ".button.programs"));
            programsButton.setTextColor(Theme.PRIMARY_COLOR);
            programsButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            programsButton.setBackground(backgroundFactory.newBackgroundDrawable());
            programsButton.setOnClickListener(button -> RouterContainer.getInstance().pushNavigate(
                    new PodcastRadioView(context)));
            LayoutParams programsParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            programsParams.setMargins(0, 0, dp(8), 0);
            buttonsLayout.addView(programsButton, programsParams);
        }

        Button logoutButton = new Button(context);
        logoutButton.setText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
        logoutButton.setTextColor(Theme.PRIMARY_COLOR);
        logoutButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        var background2 = backgroundFactory.newBackgroundDrawable();
        logoutButton.setBackground(background2);
        logoutButton.setOnClickListener(b -> {
            clientLoginService.logout();
        });
        buttonsLayout.addView(logoutButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        LayoutParams topPanelLayoutParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topPanelLayoutParams.setMargins(0, dp(32), 0, dp(32));
        addView(topPanel, topPanelLayoutParams);

        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        addView(progressBar, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        TextView errorText = new TextView(context);
        errorText.setText(I18n.get(MusicHud.MOD_ID + ".text.accountLoadError"));
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        errorText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        errorText.setVisibility(GONE);
        addView(errorText, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        addView(content);

        {
            myPlaylistsContent = new LinearLayout(context);
            myPlaylistsContent.setOrientation(VERTICAL);
            myPlaylistsContent.setVisibility(GONE);
            LayoutParams myPlaylistsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            myPlaylistsContentParams.setMargins(0, 0, 0, dp(32));
            content.addView(myPlaylistsContent, myPlaylistsContentParams);

            TextView myPlaylistsText = new TextView(context);
            myPlaylistsText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            myPlaylistsText.setTextSize(Theme.TEXT_SIZE_LARGE);
            myPlaylistsText.setText(I18n.get(MusicHud.MOD_ID + ".text.myPlaylists"));
            LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            titleParam.setMargins(0, 0, 0, dp(16));
            myPlaylistsContent.addView(myPlaylistsText, titleParam);

            myPlaylistCards = new FlexWrapLayout(context);
            myPlaylistsContent.addView(myPlaylistCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        {
            mySubscribedPlaylistsContent = new LinearLayout(context);
            mySubscribedPlaylistsContent.setOrientation(VERTICAL);
            mySubscribedPlaylistsContent.setVisibility(GONE);
            LayoutParams mySubscribedPlaylistsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            mySubscribedPlaylistsContentParams.setMargins(0, 0, 0, dp(32));
            content.addView(mySubscribedPlaylistsContent, mySubscribedPlaylistsContentParams);

            TextView subscribedPlaylistsText = new TextView(context);
            subscribedPlaylistsText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            subscribedPlaylistsText.setTextSize(Theme.TEXT_SIZE_LARGE);
            subscribedPlaylistsText.setText(I18n.get(MusicHud.MOD_ID + ".text.mySubscribedPlaylists"));
            LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            titleParam.setMargins(0, 0, 0, dp(16));
            mySubscribedPlaylistsContent.addView(subscribedPlaylistsText, titleParam);

            mySubscribedPlaylistCards = new FlexWrapLayout(context);
            mySubscribedPlaylistsContent.addView(mySubscribedPlaylistCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        {
            mySubscribedAlbumsContent = new LinearLayout(context);
            mySubscribedAlbumsContent.setOrientation(VERTICAL);
            mySubscribedAlbumsContent.setVisibility(GONE);
            LayoutParams mySubscribedAlbumsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            mySubscribedAlbumsContentParams.setMargins(0, 0, 0, dp(32));
            content.addView(mySubscribedAlbumsContent, mySubscribedAlbumsContentParams);

            TextView subscribedAlbumsText = new TextView(context);
            subscribedAlbumsText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            subscribedAlbumsText.setTextSize(Theme.TEXT_SIZE_LARGE);
            subscribedAlbumsText.setText(I18n.get(MusicHud.MOD_ID + ".text.myAlbums"));
            LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            titleParam.setMargins(0, 0, 0, dp(16));
            mySubscribedAlbumsContent.addView(subscribedAlbumsText, titleParam);

            albumCards = new FlexWrapLayout(context);
            mySubscribedAlbumsContent.addView(albumCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        {
            mySubscribedArtistsContent = new LinearLayout(context);
            mySubscribedArtistsContent.setOrientation(VERTICAL);
            mySubscribedArtistsContent.setVisibility(GONE);
            LayoutParams mySubscribedArtistsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            mySubscribedArtistsContentParams.setMargins(0, 0, 0, dp(32));
            content.addView(mySubscribedArtistsContent, mySubscribedArtistsContentParams);

            TextView artistText = new TextView(context);
            artistText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            artistText.setTextSize(Theme.TEXT_SIZE_LARGE);
            artistText.setText(I18n.get(MusicHud.MOD_ID + ".text.myArtists"));
            LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            titleParam.setMargins(0, 0, 0, dp(16));
            mySubscribedArtistsContent.addView(artistText, titleParam);

            artistCards = new FlexWrapLayout(context);
            mySubscribedArtistsContent.addView(artistCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }

        MusicService musicService = MusicService.getInstance();
        TuneWeavePlatform requestedPlatform = selectedPlatform;
        musicService.loadUserCollections(ignoreCache).thenAccept(userCollections -> {
            MuiModApi.postToUiThread(() -> {
                if (!isAttachedToWindow() || generation != refreshGeneration
                        || showingUniPlaylists || selectedPlatform != requestedPlatform) {
                    return;
                }
                unregisterCollectionListeners();
                UserCategoryPlaylists categoryPlaylists = userCollections.getUserCategoryPlaylists();
                if (categoryPlaylists == null) {
                    progressBar.setVisibility(View.GONE);
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                Playlist likeList = categoryPlaylists.getLikeList();
                boolean hasLikeList = likeList != null && likeList.getId() >= 0;
                if (hasLikeList) {
                    elementMap.computeIfAbsent(new ElementKey(Playlist.class, likeList.getId()), key -> {
                        MusicCollectionCard card = new MusicCollectionCard(context, likeList);
                        card.setTag(likeList.getId());
                        myPlaylistCards.addView(card);
                        return card;
                    });
                }
                ObservableSequencedSet<Playlist> loadedCreatedPlaylists = categoryPlaylists.getCreatedPlaylist();
                ObservableSequencedSet<Playlist> createdPlaylist = loadedCreatedPlaylists == null
                        ? new ObservableSequencedSet<>() : loadedCreatedPlaylists;
                createdPlaylist.forEach(playlist -> elementMap.computeIfAbsent(new ElementKey(Playlist.class, playlist.getId()), key -> {
                    MusicCollectionCard card = new MusicCollectionCard(context, playlist);
                    card.setTag(playlist.getId());
                    myPlaylistCards.addView(card);
                    return card;
                }));
                ObservableSequencedSet<Playlist> loadedSubscribedPlaylists = categoryPlaylists.getSubscribedPlaylist();
                ObservableSequencedSet<Playlist> subscribedPlaylist = loadedSubscribedPlaylists == null
                        ? new ObservableSequencedSet<>() : loadedSubscribedPlaylists;
                subscribedPlaylist.forEach(playlistCardCreator);
                playlistAddRegister = subscribedPlaylist.registerOnAdd(playlistCardCreator);
                playlistRemoveRegister = subscribedPlaylist.registerOnRemove(playlist -> {
                    MuiModApi.postToUiThread(() -> {
                        if (!isAttachedToWindow()) {
                            return;
                        }
                        View toRemove = elementMap.remove(new ElementKey(Playlist.class, playlist.getId()));
                        if (toRemove != null) {
                            mySubscribedPlaylistCards.removeView(toRemove);
                        }
                    });
                });
                myPlaylistsContent.setVisibility(!hasLikeList && createdPlaylist.isEmpty() ? GONE : VISIBLE);
                mySubscribedPlaylistsContent.setVisibility(subscribedPlaylist.isEmpty() ? GONE : VISIBLE);

                ObservableSequencedSet<Album> loadedAlbums = userCollections.getSubscribedAlbums();
                ObservableSequencedSet<Album> albums = loadedAlbums == null
                        ? new ObservableSequencedSet<>() : loadedAlbums;
                albums.forEach(albumCardCreator);
                albumAddRegister = albums.registerOnAdd(albumCardCreator);
                albumRemoveRegister = albums.registerOnRemove(album -> {
                    MuiModApi.postToUiThread(() -> {
                        if (!isAttachedToWindow()) {
                            return;
                        }
                        View toRemove = elementMap.remove(new ElementKey(Album.class, album.getId()));
                        if (toRemove != null) {
                            albumCards.removeView(toRemove);
                        }
                    });
                });
                mySubscribedAlbumsContent.setVisibility(albums.isEmpty() ? GONE : VISIBLE);

                ObservableSequencedSet<Artist> loadedArtists = userCollections.getSubscribedArtists();
                ObservableSequencedSet<Artist> artists = loadedArtists == null
                        ? new ObservableSequencedSet<>() : loadedArtists;
                artists.forEach(artistCardCreator);
                artistAddRegister = artists.registerOnAdd(artistCardCreator);
                artistRemoveRegister = artists.registerOnRemove(artist -> {
                    MuiModApi.postToUiThread(() -> {
                        if (!isAttachedToWindow()) {
                            return;
                        }
                        View toRemove = elementMap.remove(new ElementKey(Artist.class, artist.getId()));
                        if (toRemove != null) {
                            artistCards.removeView(toRemove);
                        }
                    });
                });
                mySubscribedArtistsContent.setVisibility(artists.isEmpty() ? GONE : VISIBLE);

                progressBar.setVisibility(View.GONE);
            });
        }).exceptionally((e) -> {
            MuiModApi.postToUiThread(() -> {
                if (isAttachedToWindow() && generation == refreshGeneration
                        && !showingUniPlaylists && selectedPlatform == requestedPlatform) {
                    progressBar.setVisibility(View.GONE);
                    errorText.setVisibility(View.VISIBLE);
                }
            });
            return null;
        });
    }

    private record ElementKey(Class<?> clazz, long id) {
    }
}
