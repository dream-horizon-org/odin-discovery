package com.dream11.odin.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility methods related to DNS resolution. All methods are best-effort and deliberately simple –
 * they are meant only for existence checks or quick metadata lookups, not for exhaustive DNS
 * analysis.
 */
@Slf4j
@UtilityClass
public class DnsUtil {

  /**
   * Resolves a hostname with a fallback to check for CNAME records if the initial lookup fails.
   * This handles cases where the default Java DNS resolver might not follow CNAMEs correctly, while
   * command-line tools like nslookup do.
   *
   * @param hostname The hostname to resolve.
   * @return {@code true} if the hostname has an A/AAAA record OR a CNAME record, or if an
   *     indeterminate error occurs. {@code false} only if the name genuinely does not exist
   *     (NXDOMAIN).
   */
  public static boolean resolveWithCnameCheck(String hostname) {
    log.info("Starting enhanced DNS resolve for hostname='" + hostname + "'");
    try {
      // Step 1: Attempt resolution using the standard Java method.
      // This is efficient and works for most standard A/AAAA records.
      InetAddress[] addresses = InetAddress.getAllByName(hostname);
      boolean found = addresses != null && addresses.length > 0;
      log.info(
          "Standard DNS resolve for '"
              + hostname
              + "' returned "
              + (found ? addresses.length : 0)
              + " address(es)");
      return found;
    } catch (UnknownHostException uhe) {
      // Step 2: The standard method failed. This could be a true NXDOMAIN,
      // or it could be a CNAME that the resolver didn't follow.
      // We now perform a specific CNAME lookup using JNDI.
      log.warn("Standard resolve failed for '" + hostname + "'. Falling back to JNDI CNAME check.");
      try {
        if (hasCnameRecord(hostname)) {
          log.info(
              "JNDI check found a CNAME record for '" + hostname + "'. Treating as resolvable.");
          return true; // Found a CNAME, so the domain exists.
        } else {
          // No CNAME found either. This is likely a genuine NXDOMAIN.
          log.info(
              "JNDI check found no CNAME. DNS resolve for '"
                  + hostname
                  + "' resulted in NXDOMAIN.");
          return false;
        }
      } catch (NameNotFoundException e) {
        // This exception indicates that the JNDI lookup did not find any CNAME records.
        log.warn("CNAME lookup  for '" + hostname + "' found no records.", e);
        return false; // No CNAME, so treat as NXDOMAIN.
      } catch (Exception e) {
        // The JNDI lookup itself failed. This is an indeterminate error.
        log.warn("CNAME lookup for '" + hostname + "' failed unexpectedly.", e);
        return true; // Treat as "up" but alert.
      }
    } catch (Exception e) {
      // Any other unexpected error during the initial lookup.
      log.warn("Unexpected error during standard DNS resolve for '" + hostname + "'", e);
      return true; // Treat as "up" but alert.
    }
  }

  /**
   * Uses JNDI to perform a specific DNS query for CNAME records.
   *
   * @param hostname The hostname to check.
   * @return {@code true} if a CNAME record is found, {@code false} otherwise.
   * @throws javax.naming.NamingException if the DNS query fails.
   */
  private static boolean hasCnameRecord(String hostname) throws javax.naming.NamingException {
    // Configure the JNDI environment for DNS
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
    env.put(Context.PROVIDER_URL, "dns:"); // Use default DNS configured for the machine

    DirContext dirContext = new InitialDirContext(env);
    // Ask for the "CNAME" records specifically
    Attributes attrs = dirContext.getAttributes(hostname, new String[] {"CNAME"});
    NamingEnumeration<?> enumeration = attrs.getAll();

    // We just need to know if there's at least one result.
    if (enumeration.hasMore()) {
      log.info("Found CNAME record: " + enumeration.next());
      enumeration.close();
      return true;
    }
    return false;
  }
}
