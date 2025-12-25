package com.kcpd.myfolder.ui.camera;

import com.kcpd.myfolder.data.repository.MediaRepository;
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
public final class CameraViewModel_Factory implements Factory<CameraViewModel> {
  private final Provider<MediaRepository> mediaRepositoryProvider;

  public CameraViewModel_Factory(Provider<MediaRepository> mediaRepositoryProvider) {
    this.mediaRepositoryProvider = mediaRepositoryProvider;
  }

  @Override
  public CameraViewModel get() {
    return newInstance(mediaRepositoryProvider.get());
  }

  public static CameraViewModel_Factory create(Provider<MediaRepository> mediaRepositoryProvider) {
    return new CameraViewModel_Factory(mediaRepositoryProvider);
  }

  public static CameraViewModel newInstance(MediaRepository mediaRepository) {
    return new CameraViewModel(mediaRepository);
  }
}
