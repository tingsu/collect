package org.odk.collect.android.injection.config;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.telephony.TelephonyManager;
import android.webkit.MimeTypeMap;

import androidx.work.WorkManager;

import org.javarosa.core.reference.ReferenceManager;
import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.R;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.analytics.FirebaseAnalytics;
import org.odk.collect.android.application.initialization.ApplicationInitializer;
import org.odk.collect.android.backgroundwork.JobManagerAndSchedulerBackgroundWorkManager;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.events.RxEventBus;
import org.odk.collect.android.formentry.media.AudioHelperFactory;
import org.odk.collect.android.formentry.media.ScreenContextAudioHelperFactory;
import org.odk.collect.android.formmanagement.DiskFormsSynchronizer;
import org.odk.collect.android.formmanagement.FormDownloader;
import org.odk.collect.android.formmanagement.ServerFormsDetailsFetcher;
import org.odk.collect.android.formmanagement.SyncStatusRepository;
import org.odk.collect.android.formmanagement.ServerFormsSynchronizer;
import org.odk.collect.android.forms.DatabaseFormRepository;
import org.odk.collect.android.forms.DatabaseMediaFileRepository;
import org.odk.collect.android.forms.FormRepository;
import org.odk.collect.android.forms.MediaFileRepository;
import org.odk.collect.android.geo.MapProvider;
import org.odk.collect.android.jobs.CollectJobCreator;
import org.odk.collect.android.metadata.InstallIDProvider;
import org.odk.collect.android.metadata.SharedPreferencesInstallIDProvider;
import org.odk.collect.android.network.ConnectivityProvider;
import org.odk.collect.android.network.NetworkStateProvider;
import org.odk.collect.android.openrosa.CollectThenSystemContentTypeMapper;
import org.odk.collect.android.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.openrosa.OpenRosaXmlFetcher;
import org.odk.collect.android.openrosa.api.FormListApi;
import org.odk.collect.android.openrosa.api.OpenRosaFormListApi;
import org.odk.collect.android.openrosa.okhttp.OkHttpConnection;
import org.odk.collect.android.openrosa.okhttp.OkHttpOpenRosaServerClientProvider;
import org.odk.collect.android.preferences.AdminSharedPreferences;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.PreferencesProvider;
import org.odk.collect.android.preferences.qr.CachingQRCodeGenerator;
import org.odk.collect.android.preferences.qr.QRCodeGenerator;
import org.odk.collect.android.storage.StorageInitializer;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageStateProvider;
import org.odk.collect.android.storage.migration.StorageEraser;
import org.odk.collect.android.storage.migration.StorageMigrationRepository;
import org.odk.collect.android.storage.migration.StorageMigrator;
import org.odk.collect.android.utilities.ActivityAvailability;
import org.odk.collect.android.utilities.AdminPasswordProvider;
import org.odk.collect.android.utilities.AndroidUserAgent;
import org.odk.collect.android.utilities.DeviceDetailsProvider;
import org.odk.collect.android.utilities.FileProvider;
import org.odk.collect.android.utilities.FormListDownloader;
import org.odk.collect.android.utilities.FormsDirDiskFormsSynchronizer;
import org.odk.collect.android.utilities.MultiFormDownloader;
import org.odk.collect.android.utilities.PermissionUtils;
import org.odk.collect.android.utilities.WebCredentialsUtils;
import org.odk.collect.android.version.VersionInformation;
import org.odk.collect.async.CoroutineAndWorkManagerScheduler;
import org.odk.collect.async.Scheduler;
import org.odk.collect.android.backgroundwork.BackgroundWorkManager;
import org.odk.collect.utilities.UserAgentProvider;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;

import static androidx.core.content.FileProvider.getUriForFile;
import static org.odk.collect.android.preferences.MetaKeys.KEY_INSTALL_ID;

/**
 * Add dependency providers here (annotated with @Provides)
 * for objects you need to inject
 */
@Module
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class AppDependencyModule {

    @Provides
    Context context(Application application) {
        return application;
    }

    @Provides
    public InstancesDao provideInstancesDao() {
        return new InstancesDao();
    }

    @Provides
    public FormsDao provideFormsDao() {
        return new FormsDao();
    }

    @Provides
    @Singleton
    RxEventBus provideRxEventBus() {
        return new RxEventBus();
    }

    @Provides
    MimeTypeMap provideMimeTypeMap() {
        return MimeTypeMap.getSingleton();
    }

    @Provides
    @Singleton
    UserAgentProvider providesUserAgent() {
        return new AndroidUserAgent();
    }

    @Provides
    @Singleton
    public OpenRosaHttpInterface provideHttpInterface(MimeTypeMap mimeTypeMap, UserAgentProvider userAgentProvider) {
        return new OkHttpConnection(
                new OkHttpOpenRosaServerClientProvider(new OkHttpClient()),
                new CollectThenSystemContentTypeMapper(mimeTypeMap),
                userAgentProvider.getUserAgent()
        );
    }

    @Provides
    public OpenRosaXmlFetcher provideCollectServerClient(OpenRosaHttpInterface httpInterface, WebCredentialsUtils webCredentialsUtils) {
        return new OpenRosaXmlFetcher(httpInterface, webCredentialsUtils);
    }

    @Provides
    WebCredentialsUtils provideWebCredentials() {
        return new WebCredentialsUtils();
    }

    @Provides
    FormListDownloader formListDownloader(
            Application application,
            OpenRosaXmlFetcher openRosaXMLFetcher,
            WebCredentialsUtils webCredentialsUtils) {
        return new FormListDownloader(
                application,
                openRosaXMLFetcher,
                webCredentialsUtils
        );
    }

    @Provides
    MultiFormDownloader providesMultiFormDownloader(FormsDao formsDao, OpenRosaXmlFetcher openRosaXMLFetcher) {
        return new MultiFormDownloader(formsDao, openRosaXMLFetcher);
    }

    @Provides
    FormDownloader providesFormDownloader(MultiFormDownloader multiFormDownloader) {
        return multiFormDownloader;
    }

    @Provides
    @Singleton
    public Analytics providesAnalytics(Application application, GeneralSharedPreferences generalSharedPreferences) {
        com.google.firebase.analytics.FirebaseAnalytics firebaseAnalyticsInstance = com.google.firebase.analytics.FirebaseAnalytics.getInstance(application);
        return new FirebaseAnalytics(firebaseAnalyticsInstance, generalSharedPreferences);
    }

    @Provides
    public PermissionUtils providesPermissionUtils() {
        return new PermissionUtils();
    }

    @Provides
    public ReferenceManager providesReferenceManager() {
        return ReferenceManager.instance();
    }

    @Provides
    public AudioHelperFactory providesAudioHelperFactory(Scheduler scheduler) {
        return new ScreenContextAudioHelperFactory(scheduler, MediaPlayer::new);
    }

    @Provides
    public ActivityAvailability providesActivityAvailability(Context context) {
        return new ActivityAvailability(context);
    }

    @Provides
    @Singleton
    public StorageMigrationRepository providesStorageMigrationRepository() {
        return new StorageMigrationRepository();
    }

    @Provides
    @Singleton
    public StorageInitializer providesStorageInitializer() {
        return new StorageInitializer();
    }

    @Provides
    StorageMigrator providesStorageMigrator(StoragePathProvider storagePathProvider, StorageStateProvider storageStateProvider, StorageMigrationRepository storageMigrationRepository, ReferenceManager referenceManager, BackgroundWorkManager backgroundWorkManager, Analytics analytics) {
        StorageEraser storageEraser = new StorageEraser(storagePathProvider);

        return new StorageMigrator(storagePathProvider, storageStateProvider, storageEraser, storageMigrationRepository, GeneralSharedPreferences.getInstance(), referenceManager, backgroundWorkManager, analytics);
    }

    @Provides
    PreferencesProvider providesPreferencesProvider(Context context) {
        return new PreferencesProvider(context);
    }

    @Provides
    InstallIDProvider providesInstallIDProvider(PreferencesProvider preferencesProvider) {
        return new SharedPreferencesInstallIDProvider(preferencesProvider.getMetaSharedPreferences(), KEY_INSTALL_ID);
    }

    @Provides
    public DeviceDetailsProvider providesDeviceDetailsProvider(Context context) {
        TelephonyManager telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        return new DeviceDetailsProvider() {

            @Override
            @SuppressLint({"MissingPermission", "HardwareIds"})
            public String getDeviceId() {
                return telMgr.getDeviceId();
            }

            @Override
            @SuppressLint({"MissingPermission", "HardwareIds"})
            public String getLine1Number() {
                return telMgr.getLine1Number();
            }

            @Override
            @SuppressLint({"MissingPermission", "HardwareIds"})
            public String getSubscriberId() {
                return telMgr.getSubscriberId();
            }

            @Override
            @SuppressLint({"MissingPermission", "HardwareIds"})
            public String getSimSerialNumber() {
                return telMgr.getSimSerialNumber();
            }
        };
    }

    @Provides
    @Singleton
    GeneralSharedPreferences providesGeneralSharedPreferences(Context context) {
        return new GeneralSharedPreferences(context);
    }

    @Provides
    @Singleton
    AdminSharedPreferences providesAdminSharedPreferences(Context context) {
        return new AdminSharedPreferences(context);
    }

    @Provides
    @Singleton
    public MapProvider providesMapProvider() {
        return new MapProvider();
    }

    @Provides
    public StorageStateProvider providesStorageStateProvider() {
        return new StorageStateProvider();
    }

    @Provides
    public StoragePathProvider providesStoragePathProvider() {
        return new StoragePathProvider();
    }

    @Provides
    public AdminPasswordProvider providesAdminPasswordProvider() {
        return new AdminPasswordProvider(AdminSharedPreferences.getInstance());
    }

    @Provides
    public CollectJobCreator providesCollectJobCreator() {
        return new CollectJobCreator();
    }

    @Provides
    public BackgroundWorkManager providesBackgroundWorkManager(Scheduler scheduler) {
        return new JobManagerAndSchedulerBackgroundWorkManager(scheduler);
    }

    @Provides
    public NetworkStateProvider providesConnectivityProvider() {
        return new ConnectivityProvider();
    }

    @Provides
    public QRCodeGenerator providesQRCodeGenerator(Context context) {
        return new CachingQRCodeGenerator();
    }

    @Provides
    public VersionInformation providesVersionInformation() {
        return new VersionInformation(() -> BuildConfig.VERSION_NAME);
    }

    @Provides
    public FileProvider providesFileProvider(Context context) {
        return filePath -> getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", new File(filePath));
    }

    @Provides
    public WorkManager providesWorkManager(Context context) {
        return WorkManager.getInstance(context);
    }

    @Provides
    public Scheduler providesScheduler(WorkManager workManager) {
        return new CoroutineAndWorkManagerScheduler(workManager);
    }

    @Singleton
    @Provides
    public ApplicationInitializer providesApplicationInitializer(Application application, CollectJobCreator collectJobCreator, PreferencesProvider preferencesProvider, UserAgentProvider userAgentProvider) {
        return new ApplicationInitializer(application, collectJobCreator, preferencesProvider.getMetaSharedPreferences(), userAgentProvider);
    }

    @Provides
    public FormRepository providesFormRepository() {
        return new DatabaseFormRepository();
    }

    @Provides
    public MediaFileRepository providesMediaFileRepository() {
        return new DatabaseMediaFileRepository();
    }

    @Provides
    public FormListApi providesFormAPI(GeneralSharedPreferences generalSharedPreferences, Context context, OpenRosaXmlFetcher openRosaXMLFetcher) {
        SharedPreferences generalPrefs = generalSharedPreferences.getSharedPreferences();
        String serverURL = generalPrefs.getString(GeneralKeys.KEY_SERVER_URL, context.getString(R.string.default_server_url));
        String formListPath = generalPrefs.getString(GeneralKeys.KEY_FORMLIST_URL, context.getString(R.string.default_odk_formlist));

        return new OpenRosaFormListApi(openRosaXMLFetcher, serverURL, formListPath);
    }

    @Provides
    public DiskFormsSynchronizer providesDiskFormSynchronizer() {
        return new FormsDirDiskFormsSynchronizer();
    }

    @Provides
    @Singleton
    public SyncStatusRepository providesServerFormSyncRepository() {
        return new SyncStatusRepository();
    }

    @Provides
    public ServerFormsDetailsFetcher providesServerFormDetailsFetcher(FormRepository formRepository, MediaFileRepository mediaFileRepository, FormListApi formListAPI, DiskFormsSynchronizer diskFormsSynchronizer) {
        return new ServerFormsDetailsFetcher(formRepository, mediaFileRepository, formListAPI, diskFormsSynchronizer);
    }

    @Provides
    public ServerFormsSynchronizer providesServerFormSynchronizer(ServerFormsDetailsFetcher serverFormsDetailsFetcher, FormRepository formRepository, FormDownloader formDownloader) {
        return new ServerFormsSynchronizer(serverFormsDetailsFetcher, formRepository, formDownloader);
    }
}
