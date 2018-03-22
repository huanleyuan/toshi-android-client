/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.model.local;

import android.support.annotation.Nullable;

import com.toshi.R;
import com.toshi.util.sharedPrefs.SharedPrefs;
import com.toshi.view.BaseApplication;

import java.util.ArrayList;
import java.util.List;

public class Networks {
    private static final String MAINNET_ID = "1";
    private static Networks instance;
    private List<Network> networks;

    public static Networks getInstance() {
        if (instance == null) {
            instance = new Networks();
        }
        return instance;
    }

    private Networks() {
        this.networks = loadNetworkList();
    }

    private List<Network> loadNetworkList() {
        final String[] networkArray = BaseApplication.get().getResources().getStringArray(R.array.networks);
        final List<Network> networks = new ArrayList<>();
        for (final String networkDescription : networkArray) {
            networks.add(new Network(networkDescription));
        }
        return networks;
    }

    /**
     * Get all supported Ethereum networks
     *
     * @return List of all Network
     * @see Network
     */
    public List<Network> getNetworks() {
        return this.networks;
    }

    /**
     * Get the default Ethereum network
     * N.B. This is currently just using the first item in the list of Networks
     *
     * @return The default Ethereum Network
     * @see Network
     */
    public Network getDefaultNetwork() {
        return this.networks.get(0);
    }

    /**
     * Find out if the default network is currently being used
     *
     * @return <code>true</code> if is currently using default network, <code>false</code> otherwise.
     */
    public boolean onDefaultNetwork() {
        // If the current network id is unknown then revert to default.
        if (getCurrentNetworkId() == null) return true;

        return getCurrentNetworkId().equals(getDefaultNetwork().getId());
    }

    /**
     * Find out if the current network is Ethereum Main Network
     *
     * @return <code>true</code> if is currently using Ethereum Main Network, <code>false</code> otherwise.
     */
    public boolean onMainNet() {
        return getCurrentNetwork().getId().equals(MAINNET_ID);
    }

    /**
     * Get the current Ethereum network
     *
     * @return The current Ethereum Network
     * @see Network
     */
    public Network getCurrentNetwork() {
        try {
            return getNetworkById(getCurrentNetworkId());
        } catch (final NullPointerException ex) {
            return getDefaultNetwork();
        }
    }

    private Network getNetworkById(final @Nullable String id) throws NullPointerException {
        for (final Network network : this.networks) {
            if (network.getId().equals(id)) return network;
        }
        throw new NullPointerException("No network exists with that ID");
    }

    private @Nullable String getCurrentNetworkId() {
        return SharedPrefs.INSTANCE.getCurrentNetworkId();
    }
}