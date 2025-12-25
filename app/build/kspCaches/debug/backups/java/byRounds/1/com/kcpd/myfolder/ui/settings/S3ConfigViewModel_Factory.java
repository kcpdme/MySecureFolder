package com.kcpd.myfolder.ui.settings;

import android.app.Application;
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
public final class S3ConfigViewModel_Factory implements Factory<S3ConfigViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<S3Repository> s3RepositoryProvider;

  public S3ConfigViewModel_Factory(Provider<Application> applicationProvider,
      Provider<S3Repository> s3RepositoryProvider) {
    this.applicationProvider = applicationProvider;
    this.s3RepositoryProvider = s3RepositoryProvider;
  }

  @Override
  public S3ConfigViewModel get() {
    return newInstance(applicationProvider.get(), s3RepositoryProvider.get());
  }

  public static S3ConfigViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<S3Repository> s3RepositoryProvider) {
    return new S3ConfigViewModel_Factory(applicationProvider, s3RepositoryProvider);
  }

  public static S3ConfigViewModel newInstance(Application application, S3Repository s3Repository) {
    return new S3ConfigViewModel(application, s3Repository);
  }
}
