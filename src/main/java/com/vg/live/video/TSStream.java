package com.vg.live.video;

import js.nio.ByteBuffer;
import org.jcodec.containers.mps.psi.PATSection;
import org.jcodec.containers.mps.psi.PMTSection;

import static com.vg.live.video.TSPkt.PAT_PID;

public class TSStream {
    public PATSection pat;
    public PMTSection pmt;
    public int[] pmtPIDs;
    public long startPts = -1;

    public boolean isPMT(int pid) {
        if (pmtPIDs != null) {
            for (int pmtPID : pmtPIDs) {
                if (pid == pmtPID)
                    return true;
            }
        }
        return false;
    }

    /**
     * https://en.wikipedia.org/wiki/Program-specific_information
     *
     * @param pkt
     */
    public void parsePSI(TSPkt pkt) {
        TSStream stream = this;
        if (pkt.pid == PAT_PID) {
            ByteBuffer payload = pkt.payload();
            if (pkt.payloadStart) {
                int pointerField = payload.get() & 0xff;
                if (pointerField != 0) {
                    payload.setPosition(payload.position() + pointerField);
                }
            }
            PATSection pat = PATSection.parsePAT(payload);
            stream.pat = pat;
            stream.pmtPIDs = stream.pat.getPrograms()
                    .values();
        }
        if (stream.isPMT(pkt.pid)) {
            ByteBuffer payload = pkt.payload();
            if (pkt.payloadStart) {
                int pointerField = payload.get() & 0xff;
                if (pointerField != 0) {
                    payload.setPosition(payload.position() + pointerField);
                }
            }
            PMTSection pmt = PMTSection.parsePMT(payload);
            stream.pmt = pmt;
        }
    }
}
