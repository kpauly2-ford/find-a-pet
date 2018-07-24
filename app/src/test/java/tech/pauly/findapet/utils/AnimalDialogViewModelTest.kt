package tech.pauly.findapet.utils

import android.view.View
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import tech.pauly.findapet.R
import tech.pauly.findapet.shared.events.DialogEvent

class AnimalDialogViewModelTest {
    private lateinit var subject: AnimalDialogViewModel

    private var clickPositiveButton = false
    private var clickNegativeButton = false

    @Before
    fun setup() {
        subject = spyk(AnimalDialogViewModel(DialogEvent(this,
                1,
                "body",
                2,
                3,
                { clickPositiveButton = true },
                { clickNegativeButton = true },
                "url",
                4)))
    }

    @Test
    fun init_setAllValuesFromEvent() {
        subject.apply {
            assertThat(titleText.get()).isEqualTo(1)
            assertThat(bodyText.get()).isEqualTo("body")
            assertThat(positiveButtonText.get()).isEqualTo(2)
            assertThat(negativeButtonText.get()).isEqualTo(3)
            assertThat(imageUrl.get()).isEqualTo("url")
            assertThat(primaryColor.get()).isEqualTo(4)
        }
    }

    @Test
    fun clickPositiveButton_invokeCallbackAndDismiss() {
        subject.clickPositiveButton(mockk())

        assertThat(clickPositiveButton).isTrue()
        verify { subject.dismiss() }
    }

    @Test
    fun clickNegativeButton_invokeCallbackAndDismiss() {
        subject.clickNegativeButton(mockk())

        assertThat(clickNegativeButton).isTrue()
        verify { subject.dismiss() }
    }

    @Test
    fun dismiss_fireDismissSubject() {
        val observer = subject.dismissSubject.test()

        subject.dismiss(mockk())

        observer.assertValueCount(1)
    }
}