package tech.pauly.findapet.discover;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import tech.pauly.findapet.R;
import tech.pauly.findapet.data.AnimalRepository;
import tech.pauly.findapet.data.FilterRepository;
import tech.pauly.findapet.data.models.Animal;
import tech.pauly.findapet.data.models.AnimalListResponse;
import tech.pauly.findapet.data.models.AnimalType;
import tech.pauly.findapet.data.models.FetchAnimalsRequest;
import tech.pauly.findapet.data.models.Filter;
import tech.pauly.findapet.data.models.Sex;
import tech.pauly.findapet.shared.LocationHelper;
import tech.pauly.findapet.shared.PermissionHelper;
import tech.pauly.findapet.shared.ResourceProvider;
import tech.pauly.findapet.shared.datastore.DiscoverAnimalTypeUseCase;
import tech.pauly.findapet.shared.datastore.DiscoverToolbarTitleUseCase;
import tech.pauly.findapet.shared.datastore.FilterUpdatedUseCase;
import tech.pauly.findapet.shared.datastore.TransientDataStore;
import tech.pauly.findapet.shared.events.PermissionEvent;
import tech.pauly.findapet.shared.events.ViewEventBus;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DiscoverViewModelTest {

    @Mock
    private AnimalListAdapter listAdapter;

    @Mock
    private AnimalListItemViewModel.Factory animalListItemFactory;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private TransientDataStore dataStore;

    @Mock
    private PermissionHelper permissionHelper;

    @Mock
    private ViewEventBus eventBus;

    @Mock
    private LocationHelper locationHelper;

    @Mock
    private AnimalListResponse animalListResponse;

    @Mock
    private ResourceProvider resourceProvider;

    @Mock
    private FilterRepository filterRepository;

    @Mock
    private FetchAnimalsRequest fetchAnimalsRequest;

    @Mock
    private Filter filter;

    private DiscoverViewModel subject;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        DiscoverAnimalTypeUseCase useCase = mock(DiscoverAnimalTypeUseCase.class);
        when(useCase.getAnimalType()).thenReturn(AnimalType.CAT);
        when(dataStore.get(DiscoverAnimalTypeUseCase.class)).thenReturn(useCase);
        when(permissionHelper.hasPermissions(ACCESS_FINE_LOCATION)).thenReturn(true);
        when(animalRepository.fetchAnimals(any(FetchAnimalsRequest.class))).thenReturn(Observable.just(animalListResponse));
        when(locationHelper.getCurrentLocation(anyBoolean())).thenReturn(Observable.just("zipcode"));
        when(resourceProvider.getString(R.string.chip_near_location, "zipcode")).thenReturn("Near zipcode");
        when(resourceProvider.getString(R.string.chip_near_location, "zipcode2")).thenReturn("Near zipcode2");
        when(dataStore.get(FilterUpdatedUseCase.class)).thenReturn(null);
        when(filter.getSex()).thenReturn(Sex.U);
        when(filterRepository.getCurrentFilterAndNoFilterIfEmpty()).thenReturn(Single.just(filter));
        when(fetchAnimalsRequest.getFilter()).thenReturn(filter);
        when(fetchAnimalsRequest.getAnimalType()).thenReturn(AnimalType.CAT);
        when(fetchAnimalsRequest.getLocation()).thenReturn("zipcode");
        when(fetchAnimalsRequest.getLastOffset()).thenReturn(0);

        subject = new DiscoverViewModel(listAdapter, animalListItemFactory, animalRepository, dataStore, permissionHelper, eventBus, locationHelper, resourceProvider, filterRepository);
    }

    @Test
    public void onResume_firstLoad_loadList() {
        subject.onResume();

        verify(animalRepository).fetchAnimals(any(FetchAnimalsRequest.class));
    }

    @Test
    public void onResume_notFirstLoadButFilterUpdated_loadList() {
        subject.onResume();
        clearInvocations(animalRepository);
        when(dataStore.get(FilterUpdatedUseCase.class)).thenReturn(mock(FilterUpdatedUseCase.class));

        subject.onResume();

        verify(animalRepository).fetchAnimals(any(FetchAnimalsRequest.class));
    }

    @Test
    public void onResume_notFirstLoadAndFilterNotUpdated_doNothing() {
        subject.onResume();
        clearInvocations(animalRepository);

        subject.onResume();

        verify(animalRepository, never()).fetchAnimals(any(FetchAnimalsRequest.class));
    }

    @Test
    public void requestPermissionToLoad_locationPermissionNotGranted_requestPermission() {
        when(permissionHelper.hasPermissions(ACCESS_FINE_LOCATION)).thenReturn(false);
        ArgumentCaptor<PermissionEvent> argumentCaptor = ArgumentCaptor.forClass(PermissionEvent.class);

        subject.requestPermissionToLoad();

        verify(eventBus).send(argumentCaptor.capture());
        PermissionEvent sentEvent = argumentCaptor.getValue();
        assertThat(sentEvent.getRequestCode()).isEqualTo(100);
        assertThat(sentEvent.getPermissions()[0]).isEqualTo(ACCESS_FINE_LOCATION);
    }

    @Test
    public void requestPermissionToLoad_locationPermissionGranted_usesAnimalTypeFromDataStoreAndClearsListAndStartsRefreshing() {
        when(animalRepository.fetchAnimals(any(FetchAnimalsRequest.class))).thenReturn(Observable.never());
        ArgumentCaptor<FetchAnimalsRequest> captor = ArgumentCaptor.forClass(FetchAnimalsRequest.class);

        subject.requestPermissionToLoad();

        verify(dataStore).save(new DiscoverToolbarTitleUseCase(AnimalType.CAT.getToolbarName()));
        verify(animalRepository).fetchAnimals(captor.capture());
        assertThat(captor.getValue().getLocation()).isEqualTo("zipcode");
        assertThat(captor.getValue().getAnimalType()).isEqualTo(AnimalType.CAT);
        assertThat(captor.getValue().getLastOffset()).isEqualTo(0);
        verify(listAdapter).clearAnimalItems();
        assertThat(subject.refreshing.get()).isTrue();
    }

    @Test
    public void requestPermissionToLoad_getCurrentLocation_resetsLocationAndSetsLocationChip() {
        when(animalRepository.fetchAnimals(any(FetchAnimalsRequest.class))).thenReturn(Observable.never());

        subject.requestPermissionToLoad();

        verify(locationHelper).getCurrentLocation(true);
        assertThat(subject.chipList.size()).isEqualTo(1);
        assertThat(subject.chipList.get(0).getType()).isEqualTo(Chip.Type.LOCATION);
        assertThat(subject.chipList.get(0).getText()).isEqualTo("Near zipcode");
    }

    @Test
    public void requestPermissionToLoad_getCurrentLocationASecondTime_resetsLocationChip() {
        when(animalRepository.fetchAnimals(any(FetchAnimalsRequest.class))).thenReturn(Observable.never());
        subject.requestPermissionToLoad();
        when(locationHelper.getCurrentLocation(anyBoolean())).thenReturn(Observable.just("zipcode2"));

        subject.requestPermissionToLoad();

        assertThat(subject.chipList.size()).isEqualTo(1);
        assertThat(subject.chipList.get(0).getType()).isEqualTo(Chip.Type.LOCATION);
        assertThat(subject.chipList.get(0).getText()).isEqualTo("Near zipcode2");
    }

    @Test
    public void requestPermissionToLoad_getCurrentFilterAndSexIsU_doNotAddChip() {
        when(animalRepository.fetchAnimals(any(FetchAnimalsRequest.class))).thenReturn(Observable.never());

        subject.requestPermissionToLoad();

        assertThat(subject.chipList.size()).isEqualTo(1);
    }

    @Test
    public void requestPermissionToLoad_getCurrentFilterAndSexIsNotU_addChip() {
        when(filter.getSex()).thenReturn(Sex.M);
        when(resourceProvider.getString(R.string.male)).thenReturn("Male");

        subject.requestPermissionToLoad();

        assertThat(subject.chipList.size()).isEqualTo(2);
        assertThat(subject.chipList.get(1).getText()).isEqualTo("Male");
        assertThat(subject.chipList.get(1).getType()).isEqualTo(Chip.Type.FILTER);
    }

    @Test
    public void requestPermissionToLoad_fetchAnimalsOnNext_sendAnimalListToAdapterAndStopsRefreshing() {
        Animal animal = mock(Animal.class);
        when(animalListResponse.getAnimalList()).thenReturn(Collections.singletonList(animal));
        ArgumentCaptor<List<AnimalListItemViewModel>> argumentCaptor = ArgumentCaptor.forClass(List.class);

        subject.requestPermissionToLoad();

        verify(listAdapter).setAnimalItems(argumentCaptor.capture());
        verify(animalListItemFactory).newInstance(animal);
        assertThat(subject.refreshing.get()).isFalse();
    }

    @Test
    public void requestPermissionToLoad_fetchAnimalsOnNextAndAnimalListNull_setsEmptyList() {
        when(animalListResponse.getAnimalList()).thenReturn(null);
        ArgumentCaptor<List<AnimalListItemViewModel>> argumentCaptor = ArgumentCaptor.forClass(List.class);

        subject.requestPermissionToLoad();

        verify(listAdapter).setAnimalItems(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().size()).isEqualTo(0);
    }

    @Test
    public void loadMoreAnimals_fetchAnimalsAtCurrentOffsetAndDoesNotResetLocation() {
        Animal animal = mock(Animal.class);
        when(animalListResponse.getLastOffset()).thenReturn(10);
        when(animalListResponse.getAnimalList()).thenReturn(Collections.singletonList(animal));
        subject.requestPermissionToLoad();
        ArgumentCaptor<FetchAnimalsRequest> captor = ArgumentCaptor.forClass(FetchAnimalsRequest.class);
        verify(animalRepository).fetchAnimals(captor.capture());
        clearInvocations(animalRepository);

        subject.loadMoreAnimals();

        verify(animalRepository).fetchAnimals(captor.capture());
        verify(locationHelper).getCurrentLocation(false);
        assertThat(captor.getValue().getLastOffset()).isEqualTo(10);
    }
}