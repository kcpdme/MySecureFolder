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
public final class MediaRepository_Factory implements Factory<MediaRepository> {
  private final Provider<Context> contextProvider;

  public MediaRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MediaRepository get() {
    return newInstance(contextProvider.get());
  }

  public static MediaRepository_Factory create(Provider<Context> contextProvider) {
    return new MediaRepository_Factory(contextProvider);
  }

  public static MediaRepository newInstance(Context context) {
    return new MediaRepository(context);
  }
}
