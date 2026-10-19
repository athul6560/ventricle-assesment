package com.ventricle.assessment.di

import android.content.Context
import androidx.room.Room
import com.ventricle.assessment.data.local.AppDatabase
import com.ventricle.assessment.data.local.AssessmentFormDao
import com.ventricle.assessment.data.local.PatientDao
import com.ventricle.assessment.pdf.PdfExporter
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ventricle.db")
            .build()

    @Provides
    fun providePatientDao(db: AppDatabase): PatientDao = db.patientDao()

    @Provides
    fun provideAssessmentFormDao(db: AppDatabase): AssessmentFormDao =
        db.assessmentFormDao()

    @Provides
    @Singleton
    fun providePdfExporter(@ApplicationContext context: Context): PdfExporter =
        PdfExporter(context)
}
