package com.kcpd.myfolder.data.repository;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class S3Repository_Factory implements Factory<S3Repository> {
  private final Provider<Context> contextProvider;

  public S3Repository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public S3Repository get() {
    return newInstance(contextProvider.get());
  }

  public static S3Repository_Factory create(Provider<Context> contextProvider) {
    return new S3Repository_Factory(contextProvider);
  }

  public static S3Repository newInstance(Context context) {
    return new S3Repository(context);
  }
}
