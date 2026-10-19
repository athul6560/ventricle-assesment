package com.ventricle.assessment.di

import android.content.Context
import androidx.room.Room
import com.ventricle.assessment.data.local.AssessmentDao
import com.ventricle.assessment.data.local.AssessmentDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AssessmentDatabase =
        Room.databaseBuilder(context, AssessmentDatabase::class.java, "assessment.db")
            .build()

    @Provides
    fun provideAssessmentDao(db: AssessmentDatabase): AssessmentDao =
        db.assessmentDao()
}
