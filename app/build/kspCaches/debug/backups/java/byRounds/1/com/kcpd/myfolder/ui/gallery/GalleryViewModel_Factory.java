package com.kcpd.myfolder.ui.gallery;

import android.app.Application;
import com.kcpd.myfolder.data.repository.MediaRepository;
import com.kcpd.myfolder.data.repository.S3Repository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class GalleryViewModel_Factory implements Factory<GalleryViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<MediaRepository> mediaRepositoryProvider;

  private final Provider<S3Repository> s3RepositoryProvider;

  public GalleryViewModel_Factory(Provider<Application> applicationProvider,
      Provider<MediaRepository> mediaRepositoryProvider,
      Provider<S3Repository> s3RepositoryProvider) {
    this.applicationProvider = applicationProvider;
    this.mediaRepositoryProvider = mediaRepositoryProvider;
    this.s3RepositoryProvider = s3RepositoryProvider;
  }

  @Override
  public GalleryViewModel get() {
    return newInstance(applicationProvider.get(), mediaRepositoryProvider.get(), s3RepositoryProvider.get());
  }

  public static GalleryViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<MediaRepository> mediaRepositoryProvider,
      Provider<S3Repository> s3RepositoryProvider) {
    return new GalleryViewModel_Factory(applicationProvider, mediaRepositoryProvider, s3RepositoryProvider);
  }

  public static GalleryViewModel newInstance(Application application,
      MediaRepository mediaRepository, S3Repository s3Repository) {
    return new GalleryViewModel(application, mediaRepository, s3Repository);
  }
}
