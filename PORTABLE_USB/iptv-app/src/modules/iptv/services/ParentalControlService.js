/**
 * @module ParentalControlService
 * Gestión y filtrado de contenido para adultos (+18, XXX, OnlyFans, Eroticas, etc.).
 * Proporciona bloqueo por PIN (por defecto: 0000), detección automática de categorías sensibles
 * y control de sesión temporal.
 */

const ADULT_REGEX = /(?:^|[\s_|\-[(\]])(?:xxx|adult[os]?|erotic[ao]s?|only\s*fans|onlyfans|\+18|18\+|porn[o]?|hentai|gays?|playboy|venus|sextreme|brazzers|redlight|hustler|dorcel|vixen|penthouse|babes|forbidden|prive|naughty|milf|hot\s*tv)(?:$|[\s_|\-)\]])/i;
const ADULT_EXPLICIT_REGEX = /❌❌❌|🔞|PORN|XXX|ADULTOS/i;

class ParentalControlService {
  constructor() {
    this._listeners = new Set();
  }

  /**
   * Indica si el control parental está habilitado a nivel general.
   * Por defecto: false (canales y contenido XXX visibles).
   */
  get isEnabled() {
    if (typeof localStorage === 'undefined') return false;
    return localStorage.getItem('iptv_parental_enabled') === 'true';
  }

  /**
   * Indica si el usuario ya ingresó el PIN en la sesión actual.
   */
  get isUnlocked() {
    if (typeof sessionStorage === 'undefined') return false;
    return sessionStorage.getItem('iptv_parental_unlocked') === 'true';
  }

  /**
   * Obtiene el PIN configurado (default: '0000').
   */
  get pin() {
    if (typeof localStorage === 'undefined') return '0000';
    return localStorage.getItem('iptv_parental_pin') || '0000';
  }

  /**
   * Evalúa si una categoría o título corresponde a contenido adulto / explícito.
   * @param {string} categoryName
   * @param {string} title
   * @returns {boolean}
   */
  isAdult(categoryName = '', title = '') {
    const cat = String(categoryName || '').trim();
    const t = String(title || '').trim();

    if (ADULT_EXPLICIT_REGEX.test(cat) || ADULT_EXPLICIT_REGEX.test(t)) {
      return true;
    }
    if (ADULT_REGEX.test(cat) || ADULT_REGEX.test(t)) {
      return true;
    }
    return false;
  }

  /**
   * Determina si el contenido debe ser bloqueado/ocultado actualmente.
   * @param {string} categoryName
   * @param {string} title
   * @returns {boolean}
   */
  isRestricted(categoryName = '', title = '') {
    if (!this.isEnabled) return false;
    if (this.isUnlocked) return false;
    return this.isAdult(categoryName, title);
  }

  /**
   * Intenta desbloquear la sesión mediante PIN.
   * @param {string} inputPin
   * @returns {boolean}
   */
  unlock(inputPin) {
    if (String(inputPin).trim() === this.pin) {
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem('iptv_parental_unlocked', 'true');
      }
      this._notify();
      return true;
    }
    return false;
  }

  /**
   * Bloquea inmediatamente la sesión.
   */
  lock() {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem('iptv_parental_unlocked');
    }
    this._notify();
  }

  /**
   * Cambia el PIN parental.
   * @param {string} currentPin
   * @param {string} newPin
   */
  changePin(currentPin, newPin) {
    if (String(currentPin).trim() !== this.pin) {
      return { ok: false, error: 'El PIN actual es incorrecto.' };
    }
    const cleanNew = String(newPin).trim();
    if (!/^\d{4}$/.test(cleanNew)) {
      return { ok: false, error: 'El nuevo PIN debe tener exactamente 4 dígitos numéricos.' };
    }
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('iptv_parental_pin', cleanNew);
    }
    return { ok: true };
  }

  /**
   * Activa o desactiva el control parental. Si se desactiva, requiere el PIN.
   * @param {boolean} enabled
   * @param {string} pin
   */
  setEnabled(enabled, pin = '') {
    if (!enabled && String(pin).trim() !== this.pin) {
      return { ok: false, error: 'Se requiere el PIN correcto para desactivar el control parental.' };
    }
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('iptv_parental_enabled', enabled ? 'true' : 'false');
    }
    if (!enabled) {
      // Si se desactiva, desbloquear sesión
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem('iptv_parental_unlocked', 'true');
      }
    } else {
      // Si se activa, bloquear sesión
      this.lock();
    }
    this._notify();
    return { ok: true };
  }

  /**
   * Suscribe un callback a cambios de estado del control parental.
   * @param {Function} cb
   * @returns {Function} unsubscribe
   */
  onChange(cb) {
    this._listeners.add(cb);
    return () => this._listeners.delete(cb);
  }

  _notify() {
    this._listeners.forEach((cb) => {
      try {
        cb({
          isEnabled: this.isEnabled,
          isUnlocked: this.isUnlocked
        });
      } catch (e) {
        console.error('[ParentalControlService] Error en listener:', e);
      }
    });
  }
}

export const parentalControlService = new ParentalControlService();
