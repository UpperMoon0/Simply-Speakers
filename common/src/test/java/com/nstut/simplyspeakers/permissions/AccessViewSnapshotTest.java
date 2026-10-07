package com.nstut.simplyspeakers.permissions;
import com.nstut.simplyspeakers.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AccessViewSnapshotTest {
    private final UUID owner=UUID.randomUUID(),friend=UUID.randomUUID(),stranger=UUID.randomUUID();
    private SpeakerState state(){var state=new SpeakerState();state.setOwnerUuid(owner);state.setAccessMode(SpeakerAccess.TRUSTED);state.trustPlayer(friend);return state;}
    @Test void roundTripPreservesOwnerTrustPolicyAndViewerRights(){
        var source=AccessViewSnapshot.capture(state(),owner,false,true,id -> id.equals(owner)?"Owner":"Friend");
        assertEquals(source,AccessViewSnapshot.decode(source.encode()));
        assertTrue(source.canManage());assertTrue(source.canControl());assertTrue(source.streamsAllowed());
    }
    @Test void trustedControlDoesNotGrantManagement(){var view=AccessViewSnapshot.capture(state(),friend,false,false,UUID::toString);assertTrue(view.canControl());assertFalse(view.canManage());assertFalse(view.streamsAllowed());}
    @Test void strangerCannotManageOrControl(){var view=AccessViewSnapshot.capture(state(),stranger,false,true,UUID::toString);assertFalse(view.canManage());assertFalse(view.canControl());}
    @Test void operatorCanRecoverOperatorsOnlyNetwork(){var state=state();state.setAccessMode(SpeakerAccess.OPERATORS);var ownerView=AccessViewSnapshot.capture(state,owner,false,true,UUID::toString);assertTrue(ownerView.canManage());assertFalse(ownerView.canControl());var op=AccessViewSnapshot.capture(state,stranger,true,true,UUID::toString);assertTrue(op.canManage());assertTrue(op.canControl());}
    @Test void ownershipTransferRevokesOldManagementImmediately(){var state=state();state.setOwnerUuid(friend);assertFalse(AccessViewSnapshot.capture(state,owner,false,true,UUID::toString).canManage());assertTrue(AccessViewSnapshot.capture(state,friend,false,true,UUID::toString).canManage());}
    @Test void emptySnapshotFailsClosed(){assertEquals(AccessViewSnapshot.EMPTY,AccessViewSnapshot.decode(AccessViewSnapshot.EMPTY.encode()));assertFalse(AccessViewSnapshot.EMPTY.canManage());assertFalse(AccessViewSnapshot.EMPTY.canControl());}
    @Test void maximumSnapshotFitsBoundAndCollectionIsImmutable(){var state=state();state.getTrustedPlayers().clear();for(int i=0;i<128;i++)state.trustPlayer(new UUID(0,i));var view=AccessViewSnapshot.capture(state,owner,false,true,id -> "界".repeat(64));assertTrue(view.encode().length<AccessViewSnapshot.MAX_BYTES);assertEquals(view,AccessViewSnapshot.decode(view.encode()));assertThrows(UnsupportedOperationException.class,()->view.trusted().clear());}
    @Test void malformedTruncatedAndTrailingDataFailClosed(){var data=AccessViewSnapshot.EMPTY.encode();assertThrows(IllegalArgumentException.class,()->AccessViewSnapshot.decode(Arrays.copyOf(data,data.length-1)));assertThrows(IllegalArgumentException.class,()->AccessViewSnapshot.decode(Arrays.copyOf(data,data.length+1)));assertThrows(IllegalArgumentException.class,()->AccessViewSnapshot.decode(new byte[AccessViewSnapshot.MAX_BYTES+1]));data[1]=(byte)99;assertThrows(IllegalArgumentException.class,()->AccessViewSnapshot.decode(data));}
}
