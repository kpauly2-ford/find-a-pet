package tech.pauly.findapet.discover

import com.nhaarman.mockito_kotlin.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import tech.pauly.findapet.R
import tech.pauly.findapet.data.FavoriteRepository
import tech.pauly.findapet.data.ShelterRepository
import tech.pauly.findapet.data.models.*
import tech.pauly.findapet.shared.LocationHelper
import tech.pauly.findapet.shared.ResourceProvider
import tech.pauly.findapet.shared.datastore.AnimalDetailsUseCase
import tech.pauly.findapet.shared.datastore.TransientDataStore
import tech.pauly.findapet.shared.events.OptionsMenuEvent
import tech.pauly.findapet.shared.events.OptionsMenuState
import tech.pauly.findapet.shared.events.SnackbarEvent
import tech.pauly.findapet.shared.events.ViewEventBus
import java.util.*

class AnimalDetailsViewModelTest {

    private val dataStore: TransientDataStore = mock()
    private val viewPagerAdapter: AnimalDetailsViewPagerAdapter = mock()
    private val imagesPagerAdapter: AnimalImagesPagerAdapter = mock()
    private val resourceProvider: ResourceProvider = mock {
        on { getString(R.string.altered) }.thenReturn("Altered")
        on { getString(R.string.house_broken) }.thenReturn("House Broken")
        on { getString(Age.ADULT.formattedName) }.thenReturn("Adult")
        on { getString(R.string.empty_string) }.thenReturn("")
        on { getString(R.string.distance_less_than_one) }.thenReturn("< 1 mile")
        on { getQuantityString(eq(R.plurals.distance), any()) }.thenReturn("plural miles")
    }
    private val favoriteRepository: FavoriteRepository = mock()
    private val eventBus: ViewEventBus = mock()
    private val shelterRepository: ShelterRepository = mock()
    private val locationHelper: LocationHelper = mock()

    private lateinit var subject: AnimalDetailsViewModel

    @Before
    fun setup() {
        whenever(shelterRepository.fetchShelter(any())).thenReturn(Observable.never())

        subject = AnimalDetailsViewModel(viewPagerAdapter, imagesPagerAdapter, dataStore, resourceProvider, favoriteRepository, eventBus, shelterRepository, locationHelper)
    }

    @Test
    fun onCreate_getAnimalFromUseCase_setAllBasicValues() {
        setupPageWithUseCase(setupFullAnimalUseCase())

        subject.apply {
            assertThat(name.get()).isEqualTo("name")
            assertThat(sex.get()).isEqualTo(R.string.male)
            assertThat(size.get()).isEqualTo(R.string.large)
            assertThat(age.get()).isEqualTo(R.string.adult)
        }
    }

    @Test
    fun onCreate_noUseCaseReceived_fieldsShowDefaultValues() {
        setupPageWithUseCase(null)

        subject.apply {
            assertThat(name.get()).isEqualTo("")
            assertThat(age.get()).isEqualTo(R.string.missing)
            assertThat(breeds.get()).isEqualTo("")
            assertThat(sex.get()).isEqualTo(R.string.missing)
            assertThat(size.get()).isEqualTo(R.string.missing)
            assertThat(options.get()).isEqualTo("")
            assertThat(description.get()).isEqualTo("")
            assertThat(descriptionVisibility.get()).isEqualTo(false)
            assertThat(optionsVisibility.get()).isEqualTo(false)
        }
    }

    @Test
    fun onCreate_animalDescriptionFieldIsNotNull_setDescriptionAndVisibility() {
        setupPageWithUseCase(setupFullAnimalUseCase())

        assertThat(subject.descriptionVisibility.get()).isEqualTo(true)
        assertThat(subject.description.get()).isEqualTo("description")
    }

    @Test
    fun onCreate_animalDescriptionFieldIsEmpty_setDescriptionAndVisibility() {
        val useCase = setupFullAnimalUseCase()
        whenever(useCase.animal.description).thenReturn("")

        setupPageWithUseCase(useCase)

        assertThat(subject.descriptionVisibility.get()).isEqualTo(false)
    }

    @Test
    fun onCreate_photoListPresent_addsImagesToPagerAdapter() {
        setupPageWithUseCase(setupFullAnimalUseCase())

        verify(imagesPagerAdapter).setAnimalImages(check {
            assertThat(it).hasSize(2)
            assertThat(it[0].imageUrl.get()).isEqualTo("photo1.jpg")
            assertThat(it[1].imageUrl.get()).isEqualTo("photo2.jpg")
        })
        assertThat(subject.imagesCount.get()).isEqualTo(2)
    }

    @Test
    fun onCreate_largeAnimalPhotoNotPresent_addsNoImagesToAdapter() {
        val useCase = setupFullAnimalUseCase()
        whenever(useCase.animal.photoUrlList).thenReturn(null)
        setupPageWithUseCase(useCase)

        verify(imagesPagerAdapter, never()).setAnimalImages(anyList())
        assertThat(subject.imagesCount.get()).isEqualTo(0)
    }

    @Test
    fun onCreate_animalHasNoOptions_optionsNotVisible() {
        val useCase = setupFullAnimalUseCase()
        whenever(useCase.animal.options).thenReturn(emptyList())

        setupPageWithUseCase(useCase)

        assertThat(subject.optionsVisibility.get()).isEqualTo(false)
    }

    @Test
    fun onCreate_animalHasOptions_optionsListShown() {
        setupPageWithUseCase(setupFullAnimalUseCase())

        assertThat(subject.optionsVisibility.get()).isEqualTo(true)
        assertThat(subject.options.get()).isEqualTo("Altered\nHouse Broken")
    }

    @Test
    fun imagePageChange_updateCurrentImagePosition() {
        setupPageWithUseCase(setupFullAnimalUseCase())

        subject.imagePageChange(1)

        assertThat(subject.currentImagePosition.get()).isEqualTo(1)
    }

    @Test
    fun checkFavorite_animalIdNull_doNothing() {
        subject.setupPage()

        subject.checkFavorite()

        verify(favoriteRepository, never()).isAnimalFavorited(any())
    }

    @Test
    fun checkFavorite_animalIdPresentAnimalIsFavorited_sendFavoriteEventAndDoNotShowSnackbar() {
        setupPageWithUseCase(setupFullAnimalUseCase())
        whenever(favoriteRepository.isAnimalFavorited(any())).thenReturn(Single.just(true))

        subject.checkFavorite()

        verify(favoriteRepository).isAnimalFavorited(10)
        verify(eventBus) += OptionsMenuEvent(subject, OptionsMenuState.FAVORITE)
        verify(eventBus, never()) += SnackbarEvent(subject, any())
    }

    @Test
    fun checkFavorite_animalIdPresentAnimalIsNotFavorited_sendNotFavoriteEventAndDoNotShowSnackbar() {
        setupPageWithUseCase(setupFullAnimalUseCase())
        whenever(favoriteRepository.isAnimalFavorited(any())).thenReturn(Single.just(false))

        subject.checkFavorite()

        verify(favoriteRepository).isAnimalFavorited(10)
        verify(eventBus) += OptionsMenuEvent(subject, OptionsMenuState.NOT_FAVORITE)
        verify(eventBus, never()) += SnackbarEvent(subject, any())
    }

    @Test
    fun doFavorite_animalIdNull_doNothing() {
        subject.setupPage()

        subject.changeFavorite(true)

        verify(favoriteRepository, never()).favoriteAnimal(any())
    }

    @Test
    fun doFavorite_animalIdNotNull_favoriteAnimalAndShowFavorite() {
        val useCase = setupFullAnimalUseCase()
        setupPageWithUseCase(useCase)
        whenever(favoriteRepository.favoriteAnimal(any())).thenReturn(Completable.complete())

        subject.changeFavorite(true)

        verify(favoriteRepository).favoriteAnimal(useCase.animal)
        verify(eventBus) += OptionsMenuEvent(subject, OptionsMenuState.FAVORITE)
        verify(eventBus) += SnackbarEvent(subject, R.string.favorite_snackbar_message)
    }

    @Test
    fun doUnfavorite_animalIdNull_doNothing() {
        subject.setupPage()

        subject.changeFavorite(false)

        verify(favoriteRepository, never()).unfavoriteAnimal(any())
    }

    @Test
    fun doUnfavorite_animalIdNotNull_unfavoriteAnimalAndShowNotFavorite() {
        val useCase = setupFullAnimalUseCase()
        setupPageWithUseCase(useCase)
        whenever(favoriteRepository.unfavoriteAnimal(useCase.animal)).thenReturn(Completable.complete())

        subject.changeFavorite(false)

        verify(favoriteRepository).unfavoriteAnimal(useCase.animal)
        verify(eventBus) += OptionsMenuEvent(subject, OptionsMenuState.NOT_FAVORITE)
        verify(eventBus) += SnackbarEvent(subject, R.string.unfavorite_snackbar_message)
    }

    @Test
    fun updateShelter_allFieldsPresent_setsFieldsAndGetsDistance() {
        val shelter: Shelter = mock {
            on { name }.thenReturn("name")
            on { formattedAddress }.thenReturn("address")
            on { phone }.thenReturn("phone")
            on { email }.thenReturn("email")
        }
        whenever(shelterRepository.fetchShelter(any())).thenReturn(Observable.just(shelter))
        whenever(locationHelper.getCurrentDistanceToContactInfo(any())).thenReturn(Observable.never())

        setupPageWithUseCase(setupFullAnimalUseCase())

        subject.apply {
            assertThat(contactName.get()).isEqualTo("name")
            assertThat(contactAddress.get()).isEqualTo("address")
            assertThat(contactPhone.get()).isEqualTo("phone")
            assertThat(contactEmail.get()).isEqualTo("email")
            assertThat(partialContact.get()).isFalse()
            assertThat(contactPhoneVisibility.get()).isTrue()
            assertThat(contactEmailVisibility.get()).isTrue()
        }
        verify(locationHelper).getCurrentDistanceToContactInfo(shelter)
    }

    @Test
    fun updateShelter_missingFields_setsFields() {
        val shelter: Shelter = mock {
            on { name }.thenReturn(null)
            on { formattedAddress }.thenReturn("address")
            on { phone }.thenReturn(null)
            on { email }.thenReturn(null)
        }
        whenever(shelterRepository.fetchShelter(any())).thenReturn(Observable.just(shelter))
        whenever(locationHelper.getCurrentDistanceToContactInfo(any())).thenReturn(Observable.never())

        setupPageWithUseCase(setupFullAnimalUseCase())

        subject.apply {
            assertThat(contactAddress.get()).isEqualTo("address")
            assertThat(contactPhoneVisibility.get()).isFalse()
            assertThat(contactEmailVisibility.get()).isFalse()
        }
    }

    @Test
    fun updateShelter_distanceNegativeOne_setsTextToEmpty() {
        whenever(shelterRepository.fetchShelter(any())).thenReturn(Observable.just(mock()))
        whenever(locationHelper.getCurrentDistanceToContactInfo(any())).thenReturn(Observable.just(-1))

        setupPageWithUseCase(setupFullAnimalUseCase())

        assertThat(subject.contactDistance.get()).isEqualTo("")
    }

    @Test
    fun updateShelter_distanceZero_setsTextToLessThanOne() {
        whenever(shelterRepository.fetchShelter(any())).thenReturn(Observable.just(mock()))
        whenever(locationHelper.getCurrentDistanceToContactInfo(any())).thenReturn(Observable.just(0))

        setupPageWithUseCase(setupFullAnimalUseCase())

        assertThat(subject.contactDistance.get()).isEqualTo("< 1 mile")
    }

    @Test
    fun updateShelter_distanceOverZero_setsTextToPluralsEntry() {
        whenever(shelterRepository.fetchShelter(any())).thenReturn(Observable.just(mock()))
        whenever(locationHelper.getCurrentDistanceToContactInfo(any())).thenReturn(Observable.just(1))

        setupPageWithUseCase(setupFullAnimalUseCase())

        assertThat(subject.contactDistance.get()).isEqualTo("plural miles")
    }

    private fun setupFullAnimalUseCase(): AnimalDetailsUseCase {
        val animal: Animal = mock {
            on { id }.thenReturn(10)
            on { name }.thenReturn("name")
            on { sex }.thenReturn(Sex.MALE)
            on { size }.thenReturn(AnimalSize.LARGE)
            on { age }.thenReturn(Age.ADULT)
            on { description }.thenReturn("")
            on { photoUrlList }.thenReturn(listOf("photo1.jpg", "photo2.jpg"))
            on { formattedBreedList }.thenReturn("breeds")
            on { options }.thenReturn(Arrays.asList(Option.ALTERED, Option.HOUSE_BROKEN))
            on { description }.thenReturn("description")
        }

        return mock {
            on { this.animal }.thenReturn(animal)
        }
    }

    private fun setupPageWithUseCase(useCase: AnimalDetailsUseCase?) {
        whenever(dataStore[AnimalDetailsUseCase::class]).thenReturn(useCase)
        subject.setupPage()
    }
}